package com.gxz.example.videoedit;

import java.io.Serializable;

/**
 * ================================================
 * 作    者：顾修忠-guxiuzhong@youku.com/gfj19900401@163.com
 * 版    本：
 * 创建日期：2017/3/2-下午8:52
 * 描    述：
 * 修订历史：
 * ================================================
 */

public class VideoEditInfo implements Serializable {

    public String path; //图片的sd卡路径
    public long time;//图片所在视频的时间  毫秒

    public VideoEditInfo() {
    }


    @Override
    public String toString() {
        return "VideoEditInfo{" +
                "path='" + path + '\'' +
                ", time='" + time + '\'' +
                '}';
    }
}
