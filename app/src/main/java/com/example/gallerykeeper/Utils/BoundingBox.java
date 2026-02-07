package com.example.gallerykeeper.Utils;


public class BoundingBox {
    public final float x1;
    public final float y1;
    public final float x2;
    public final float y2;
    public final float cx;
    public final float cy;
    public final float w;
    public final float h;
    public final float cnf;
    public final int cls;
    public final String clsName;

    public BoundingBox(float x1, float y1, float x2, float y2, float cx, float cy, float w, float h, float cnf, int cls, String clsName) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.cx = cx;
        this.cy = cy;
        this.w = w;
        this.h = h;
        this.cnf = cnf;
        this.cls = cls;
        this.clsName = clsName;
    }
}