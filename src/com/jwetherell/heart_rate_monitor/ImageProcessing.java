package com.jwetherell.heart_rate_monitor;

public abstract class ImageProcessing {
	//YUV420SP--8 位 YUV 格式，YUV基本在24位，三个值各为8位，用byte正好表示
	/*YUV是指亮度参量和色度参量分开表示的像素格式，
	 * 而这样分开的好处就是不但可以避免相互干扰，
	 * 还可以降低色度的采样率而不会对图像质量影响太大*/
	private static int decodeYUV420SPtoRedSum(byte[] yuv420sp, int width,int height) {
		if (yuv420sp == null)
			return 0;
		//YUV采样方法
		final int frameSize = width * height;
		
		int sum = 0;
		//下面的两个for循环都只是为了把第一个像素点的的R G B读取换算出来，就是一行一行循环读取.
		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				//计算y与对应的uv值，根据YUV420SP存储规则保证Y对应相应的U和V
				//经过摄像头得到的图像数据是经过伽马校正的YCbCr模型
//				yCbCr<-->rgb
//				Y’ = 0.257*R' + 0.504*G' + 0.098*B' + 16
//				Cb'= -0.148*R'- 0.291*G'+ 0.439*B'+ 128
//				Cr' = 0.439*R'- 0.368*G'- 0.071*B'+ 128
				int y = (0xff & ((int) yuv420sp[yp])) - 16;//0xff表示255，保证值在八位内
				if (y < 0)
					y = 0;
				if ((i & 1) == 0) {			
					//跟YUV排列格式有关
					//每四个y一个u和v,(i&1)可以有效控制过界，控制着四个y始终对应相应的同样的u和v
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}
				
				//伽马校正后的公式换算
				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);
				
				//262143二进制为18位1，伽马校正后的rgb分别为真正RGB的1024倍，共18位
				if (r < 0)
					r = 0;
				else if (r > 262143)
					r = 262143;
				if (g < 0)
					g = 0;
				else if (g > 262143)
					g = 262143;
				if (b < 0)
					b = 0;
				else if (b > 262143)
					b = 262143;
				
				//pixel为ARGB的值，移位放入得出
				int pixel = 0xff000000 | ((r << 6) & 0xff0000)
						| ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
				int red = (pixel >> 16) & 0xff;//pixel是rgb总值，这里求出R值即可
				sum += red;
			}
		}
		return sum;
	}


	public static int decodeYUV420SPtoRedAvg(byte[] yuv420sp, int width,
			int height) {
		if (yuv420sp == null)
			return 0;
		final int frameSize = width * height;
		int sum = decodeYUV420SPtoRedSum(yuv420sp, width, height);
		return (sum / frameSize);
	}
}