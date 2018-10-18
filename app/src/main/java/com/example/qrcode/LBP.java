package com.example.qrcode;

import android.util.Log;
import android.widget.ImageView;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Created by zhyao on 18-2-4.
 */

public class LBP {
    private byte[] table;
    public LBP(){
        table=new byte[256];
        byte dim=0;
        for(int i=0;i<256;i++){
            if(get_hop_count(i)<=2){
                table[i]=++dim;
            }
        }
    }

    public void calc_hist(Mat src,Mat hist,int hs,int ws,int dim){
        int maskh=src.rows()/hs;
        int maskw=src.cols()/ws;

        int histSize=dim;

        for(int i=0;i<hs;i++){
            for (int j=0;j<ws;j++){
                Mat proc_hist=Mat.zeros(histSize,1,CvType.CV_32FC1);
                Mat proc_seg=new Mat(src, new Rect(j*maskw,i*maskh,maskw,maskh));
                Mat mask = Mat.ones(proc_seg.size(),proc_seg.type());
                Imgproc.calcHist(Arrays.asList(proc_seg),new MatOfInt(0),mask,
                        proc_hist,new MatOfInt(histSize),new MatOfFloat(0,59));
                Core.normalize(proc_hist,proc_hist,1.0,0.0,Core.NORM_L2);
                proc_hist=proc_hist.t();
                proc_hist.copyTo(hist.row(i*ws+j));
            }
        }

        hist=hist.reshape(0,1);
    }

    public void im2ulbp(Mat src,Mat dst){
        if(src.depth()!=0){
            Log.d("[ERROR]:","unsupport Image Type.");
            return ;
        }
        else if(src.channels()>=3){
            Imgproc.cvtColor(src,src,Imgproc.COLOR_BGR2GRAY);
        }

        final int cols=src.cols();
        final int rows=src.rows();

        Core.copyMakeBorder(src,src,1,1,1,1,Core.BORDER_REPLICATE);


        byte[] src_byte=new byte[(cols+2)*(rows+2)];
        byte[] dst_byte=new byte[cols*rows];
        src.put(0,0,src_byte);

        for(int i=1;i<=rows;i++){
            for(int j=1;j<=cols;j++){
                int code=0;
                byte center=src_byte[cols*i+j];
                code|=(src_byte[cols*(i-1)+j-1]>=center?(1<<7):0);
                code|=(src_byte[cols*(i-1)+j+1]>=center?(1<<1):0);
                code|=(src_byte[cols*(i-1)+j]>=center?1:0);
                code|=(src_byte[cols*(i+1)+j-1]>=center?(1<<5):0);
                code|=(src_byte[cols*(i+1)+j]>=center?(1<<4):0);
                code|=(src_byte[cols*(i+1)+j+1]>=center?(1<<3):0);
                code|=(src_byte[cols*i+j-1]>=center?(1<<6):0);
                code|=(src_byte[cols*i+j+1]>=center?(1<<2):0);

                dst_byte[cols*(i-1)+j-1]=table[code];
            }
        }
        dst.get(0,0,dst_byte);
    }

    public int get_hop_count(int code){
        int k=7;
        int cnt=0;

        int []a=new int[8];
        code%=256;
        while(code>0){
            a[k] = code & 1;
            code = code>>1;
            k--;
        }

        for(k=0;k<7;k++){
            if(a[k]!=a[k+1]) cnt++;
        }

        if(a[0]!=a[7]) cnt++;

        return cnt;
    }

    public void calc_hist_mask(Mat src,Mat hist,int dim,Mat mask){
        int histSize=dim;

        hist=Mat.zeros(dim,1,CvType.CV_32FC1);
        Imgproc.calcHist(Arrays.asList(src),new MatOfInt(0),mask,
                hist,new MatOfInt(histSize),new MatOfFloat(0f,59f));
        Core.normalize(hist,hist,1.0,0.0,Core.NORM_L2);

        hist=hist.t();
    }
}
