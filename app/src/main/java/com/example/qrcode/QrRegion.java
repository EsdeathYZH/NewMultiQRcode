package com.example.qrcode;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.dtr.zxing.activity.MyCaptureActivity;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.SVM;

import java.util.ArrayList;
import java.util.Comparator;

import static java.util.Collections.max;
import static java.util.Collections.min;
import static java.util.Collections.sort;

public class QrRegion{
    private CameraActivity activity;
    private Mat img;
    private int[] seg;
    private int nb_segs;
    private int nb_qrcs;

    private int seg_cols;
    private int seg_rows;

    private ArrayList<ArrayList<Point>> points;
    private Rect[] bboxs;
    private ArrayList<Neighbor> neighbors;
    private int[] sizes;
    private int[] is_qr_seg;

    private int[][] is_neighbor;

    private LBP f_lbp;
    private SVM f_svm;

    public QrRegion(CameraActivity activity, Mat p1, Mat p2, int p3){
        this.img = p1.clone();
        this.activity=activity;
        this.activity.clearBbox();

        p2.get(0,0,this.seg);
        seg_cols = p2.cols();
        seg_rows = p2.rows();
        this.nb_segs = p3;

        this.sizes = new int[nb_segs];
        this.is_qr_seg = new int[nb_segs];

        this.bboxs = new Rect[nb_segs];
        this.neighbors = new ArrayList<>();
        this.points = new ArrayList<ArrayList<Point>>(nb_segs);

        is_neighbor = new int[nb_segs][nb_segs];

        f_lbp=new LBP();
        f_svm=SVM.load("android.resource://"+activity.getPackageName()+"/"+R.raw.standard);

        nb_qrcs = 0;

        for(int i=0; i<this.nb_segs; i++){
            sizes[i] = 0;
            is_qr_seg[i] = 0;
        }
    }

    public void process(){
        for(int i=0; i<seg_rows; i++){            //i<seg_rows?  j<seg_cols?
            for(int j=0; j<seg_cols; j++){
                points.get(seg[seg_cols*i+j]).add(new Point(j,i));
                sizes[seg[seg_cols*i+j]]++;

                if(i>0 && j>0){
                    is_neighbor[seg[seg_cols*i+j]][seg[seg_cols*i+j]-1] = 1;
                    is_neighbor[seg[seg_cols*i+j]-1][seg[seg_cols*i+j]] = 1;

                    is_neighbor[seg[seg_cols*i+j]][seg[seg_cols*(i-1)+j]] = 1;
                    is_neighbor[seg[seg_cols*(i-1)+j]][seg[seg_cols*i+j]] = 1;

                    is_neighbor[seg[seg_cols*i+j]][seg[seg_cols*(i-1)+j-1]] = 1;
                    is_neighbor[seg[seg_cols*(i-1)+j-1]][seg[seg_cols*i+j]] = 1;
                }
            }
        }

        for(int i=0; i<nb_segs; i++){
            MatOfPoint matOfPoint=new MatOfPoint();
            matOfPoint.fromList(points.get(i));
            bboxs[i]=Imgproc.boundingRect(matOfPoint);
        }

        for(int s1=0; s1<nb_segs; s1++){
            for(int s2=s1; s2<nb_segs; s2++){
                if(is_neighbor[s1][s2]==1){
                    double simlarity;

                    int x1=Math.min(bboxs[s1].x,bboxs[s2].x);
                    int y1=Math.min(bboxs[s1].y,bboxs[s2].y);
                    int x2=Math.max(bboxs[s1].x+bboxs[s1].width,bboxs[s2].x+bboxs[s2].width);
                    int y2=Math.max(bboxs[s1].y+bboxs[s1].height,bboxs[s2].y+bboxs[s2].height);
                    Rect bbox_u=new Rect(new Point(x1,y1),new Point(x2,y2));

                    if(s1==s2){
                        simlarity = sizes[s1]/bbox_u.area();
                    }else{
                        simlarity = (sizes[s1]+sizes[s2])/bbox_u.area();
                    }

                    neighbors.add(new Neighbor(s1,s2,simlarity));
                }
            }
        }
        sort(neighbors, new Comparator<Neighbor>() {
            @Override
            public int compare(Neighbor o1, Neighbor o2) {
                return (o1.simlarity-o2.simlarity)>0?1:-1;
            }
        });
    }

    public void get_bbox(){
        nb_qrcs = 0;
        this.activity.clearBbox();

        int cnt=0;
        while(neighbors.size()>0){
            Neighbor proc_neighbor=neighbors.get(neighbors.size()-1);
            neighbors.remove(neighbors.size()-1);
            cnt++;

            int s1=proc_neighbor.from;
            int s2=proc_neighbor.to;
            double soU=proc_neighbor.simlarity;

            if(soU<=0.6) break;

            if(is_qr_seg[s1]==1||is_qr_seg[s2]==1){
                Log.d("One_SEG","[Seg"+cnt+"] 0 QRC,skip by algorithm.");
                continue;
            }

            int x1=Math.min(bboxs[s1].x,bboxs[s2].x);
            int y1=Math.min(bboxs[s1].y,bboxs[s2].y);
            int x2=Math.max(bboxs[s1].x+bboxs[s1].width,bboxs[s2].x+bboxs[s2].width);
            int y2=Math.max(bboxs[s1].y+bboxs[s1].height,bboxs[s2].y+bboxs[s2].height);
            Rect bbox_u=new Rect(new Point(x1,y1),new Point(x2,y2));

            double bbox_ratio=(double)bbox_u.height/bbox_u.width;

            boolean flag1=(bbox_ratio>=0.9)&&(bbox_ratio<=1.1);
            boolean flag2=(soU>=0.78)&&(soU<=0.85);

            if(!flag1||!flag2){
                neighbors.add(new Neighbor(s1,s2,soU-0.1));
                sort(neighbors, new Comparator<Neighbor>() {
                    @Override
                    public int compare(Neighbor o1, Neighbor o2) {
                        return (o1.simlarity-o2.simlarity)>0?1:-1;
                    }
                });
                Log.d("One_SEG","[Seg"+cnt+"] 0 QRC,skip by algorithm.");
                continue;
            }

            int []mask=new int[seg_rows*seg_cols];
            for(int i=0; i<seg_rows; i++){
                for(int j=0;j<seg_cols;j++){
                    if(seg[i*seg_cols+j]==s1||seg[i*seg_cols+j]==s2){
                        mask[i*seg_cols+j]=1;
                    }
                }
            }
            Mat proc_mask=new Mat(seg_rows,seg_cols,CvType.CV_32S);
            proc_mask.put(0,0,mask);
            Mat proc_seg=new Mat(),proc_lbp=new Mat();
            img.copyTo(proc_seg,proc_mask);

            Imgproc.resize(proc_seg,proc_seg,new Size(120,120));

            Mat proc_hist_feature=new Mat(1,170,CvType.CV_32FC1);
            Mat hist1=new Mat(),hist2=new Mat();
            f_lbp.im2ulbp(proc_seg,proc_lbp);
            f_lbp.calc_hist(proc_lbp,hist1,1,1,10);
            f_lbp.calc_hist(proc_lbp,hist2,4,4,10);

            hist1.copyTo(proc_hist_feature.colRange(0,10));
            hist2.copyTo(proc_hist_feature.colRange(10,170));

            float qrflag=f_svm.predict(proc_hist_feature);
            if(qrflag>0){
                is_qr_seg[s1]=1;
                is_qr_seg[s2]=1;

                activity.addBbox(bbox_u);
                nb_qrcs++;
            }
        }
    }
}

