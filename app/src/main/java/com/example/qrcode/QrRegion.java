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
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.SVM;

import java.io.File;
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
    private Mat[] seg_mat;
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
        this.seg = new int[p2.width()*p2.height()];
        p2.get(0,0,this.seg);
        seg_cols = p2.cols();
        seg_rows = p2.rows();
        this.nb_segs = p3;

        this.sizes = new int[nb_segs];
        this.is_qr_seg = new int[nb_segs];

        this.bboxs = new Rect[nb_segs];
        this.seg_mat = new Mat[nb_segs];
        this.neighbors = new ArrayList<>();
        this.points = new ArrayList<ArrayList<Point>>(nb_segs);

        for(int i=0; i<nb_segs; i++){
            this.points.add(new ArrayList<Point>());
        }

        is_neighbor = new int[nb_segs][nb_segs];

        f_lbp=new LBP();
        f_svm=SVM.load(Environment.getExternalStorageDirectory().getAbsolutePath()+"/svm_model.xml");

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
                    is_neighbor[seg[seg_cols*i+j]][seg[seg_cols*i+j-1]] = 1;
                    is_neighbor[seg[seg_cols*i+j-1]][seg[seg_cols*i+j]] = 1;

                    is_neighbor[seg[seg_cols*i+j]][seg[seg_cols*(i-1)+j]] = 1;
                    is_neighbor[seg[seg_cols*(i-1)+j]][seg[seg_cols*i+j]] = 1;

                    is_neighbor[seg[seg_cols*i+j]][seg[seg_cols*(i-1)+j-1]] = 1;
                    is_neighbor[seg[seg_cols*(i-1)+j-1]][seg[seg_cols*i+j]] = 1;
                }
            }
        }

        for(int i=0; i<nb_segs; i++){
            Mat temp_img =new Mat();
            byte []mask=new byte[seg_rows*seg_cols*4];
            for(int j=0; j<seg_rows; j++){
                for(int k=0;k<seg_cols;k++){
                    if(seg[j*seg_cols+k]==i){
                        mask[(j*seg_cols+k)*4]=1;
                        mask[(j*seg_cols+k)*4+1]=1;
                        mask[(j*seg_cols+k)*4+2]=1;
                        mask[(j*seg_cols+k)*4+3]=1;
                    }
                }
            }
            Mat proc_mask=new Mat(seg_rows,seg_cols,CvType.CV_8UC4);
            proc_mask.put(0,0,mask);
            img.copyTo(temp_img,proc_mask);

            MatOfPoint matOfPoint=new MatOfPoint();
            matOfPoint.fromList(points.get(i));
            bboxs[i]=Imgproc.boundingRect(matOfPoint);
            seg_mat[i]=temp_img.submat(bboxs[i]);
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

    public Mat[] get_seg_mat(){
        return seg_mat;
    }

    public int get_bbox(){
        nb_qrcs = 0;
        this.activity.clearBbox();

        int cnt=0;
        while(neighbors.size()>0){
            Neighbor proc_neighbor=neighbors.get(neighbors.size()-1);
            neighbors.remove(neighbors.size()-1);


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
            boolean flag2=(soU>=0.70)&&(soU<=0.9);

            if(!flag1){
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

            byte []mask=new byte[seg_rows*seg_cols*4];
            for(int i=0; i<seg_rows; i++){
                for(int j=0;j<seg_cols;j++){
                    if(seg[i*seg_cols+j]==s1||seg[i*seg_cols+j]==s2){
                        mask[(i*seg_cols+j)*4]=1;
                        mask[(i*seg_cols+j)*4+1]=1;
                        mask[(i*seg_cols+j)*4+2]=1;
                        mask[(i*seg_cols+j)*4+3]=1;
                    }
                }
            }
            Mat proc_mask=new Mat(seg_rows,seg_cols,CvType.CV_8UC4);
            proc_mask.put(0,0,mask);
            Mat proc_seg=new Mat();
            img.copyTo(proc_seg,proc_mask);
            Imgproc.resize(proc_seg,proc_seg,new Size(120,120));

            Mat proc_hist_feature=new Mat(1,170,CvType.CV_32FC1);
            Mat proc_lbp=Mat.zeros(proc_seg.rows(),proc_seg.cols(),CvType.CV_8UC1);

            f_lbp.im2ulbp(proc_seg,proc_lbp);

            Mat hist1 = Mat.zeros(1,10, CvType.CV_32FC1);
            Mat hist2 = Mat.zeros(16,10, CvType.CV_32FC1);
            f_lbp.calc_hist(proc_lbp,hist1,1,1,10);
            f_lbp.calc_hist(proc_lbp,hist2,4,4,10);

            hist1.copyTo(proc_hist_feature.colRange(0,10));
            hist2.copyTo(proc_hist_feature.colRange(10,170));

            float qrflag=f_svm.predict(proc_hist_feature);
            cnt++;
            if(qrflag>0){
                is_qr_seg[s1]=1;
                is_qr_seg[s2]=1;

                activity.addBbox(bbox_u);
                nb_qrcs++;
            }
        }
        return cnt;
    }
}

