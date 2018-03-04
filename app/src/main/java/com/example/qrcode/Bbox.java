package com.example.qrcode;

/**
 * Created by zhyao on 18-2-2.
 */

public class Bbox {
    public int x;
    public int y;
    public int width;

    public void move(int delta_x,int delta_y,double scale){
        this.x+=delta_x;
        this.y+=delta_y;
        this.width*=scale;
    }

}
