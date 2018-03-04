package com.example.qrcode;

/**
 * Created by zhyao on 18-2-4.
 */

public class Neighbor {
    int from;
    int to;
    double simlarity;

    public Neighbor(int p1,int p2,double p3){
        this.from=p1;
        this.to=p2;
        this.simlarity=p3;
    }
}
