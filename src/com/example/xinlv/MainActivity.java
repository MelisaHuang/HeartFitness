package com.example.xinlv;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;


import com.jwetherell.heart_rate_monitor.ImageProcessing;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.Toast;
//import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
	//	曲线
	private Timer timer = new Timer();
	private TimerTask task;
	private TimerTask task1;
	private static int gx;
	private static int j;
	
	private static double flag=1;
	private Handler handler;
	private Handler handler2;
	private String title = "pulse";
	private XYSeries series;//点的集合
	private XYMultipleSeriesDataset mDataset;//数据集的实例
	private GraphicalView chart;//封装图标的视图
	private XYMultipleSeriesRenderer renderer;//渲染器
	private Context context;
	private int addX = -1;
	double addY;
	int[] xv = new int[300];
	int[] yv = new int[300];
	int[] hua=new int[]{9,10,11,12,13,14,13,12,11,10,9,8,7,6,7,8,9,10,11,10,10};

	//	private static final String TAG = "HeartRateMonitor";
	//使用 AtomicBoolean 高效并发处理 “只初始化一次” 的功能要求
	private static final AtomicBoolean processing = new AtomicBoolean(false);
	private static SurfaceView preview = null;
	private static SurfaceHolder previewHolder = null;
	private static Camera camera = null;
	//	private static View image = null;
	private static TextView text = null;
	private static TextView text1 = null;
	private static TextView text2 = null;
	private static WakeLock wakeLock = null;
	private static int averageIndex = 0;
	private static final int averageArraySize = 4;
	private static final int[] averageArray = new int[averageArraySize];

	public static enum TYPE {
		GREEN, RED//用来标记跳与不跳
	};

	private static TYPE currentType = TYPE.GREEN;

	public static TYPE getCurrent() {
		return currentType;
	}

	private static int beatsIndex = 0;
	private static final int beatsArraySize = 3;
	private static final int[] beatsArray = new int[beatsArraySize];
	private static double beats = 0;//脉冲数目，也就是波峰数
	private static int beatsdata=0;//记录心跳的全局变量
	private static long startTime = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//曲线
		context = getApplicationContext();

		//这里获得main界面上的布局，下面会把图表画在这个布局里面
		LinearLayout layout = (LinearLayout)findViewById(R.id.linearLayout1);

		//这个类用来放置曲线上的所有点，是一个点的集合，根据这些点画出曲线,初始化点集，titile为表格名字
		series = new XYSeries(title);

		//创建一个数据集的实例，这个数据集将被用来创建图表
		mDataset = new XYMultipleSeriesDataset();

		//将点集添加到这个数据集中
		mDataset.addSeries(series);

		//以下都是曲线的样式和属性等等的设置，renderer相当于一个用来给图表做渲染的句柄
		int color = Color.GREEN;
		PointStyle style = PointStyle.CIRCLE;
		renderer = buildRenderer(color, style, true);//建立renderer

		//设置好图表的样式，查找api就可以了,设置renderer
		setChartSettings(renderer, "X", "Y", 0, 300, 4, 16, Color.WHITE, Color.WHITE);

		//生成折线图表，linechartview-折线图,返回值为graphicalview类型
		chart = ChartFactory.getLineChartView(context, mDataset, renderer);

		//将图表添加到布局中去，addView(ViewGroup view, index)在指定的index处添加一个view，并且充满整个父控件
		layout.addView(chart, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));


		/*	       thread = new Thread(){
	    	   public void arrayList(int u) { 
	    		   ArrayList arrayList = new ArrayList();
	    		   arrayList.add(HardwareControler.readADC());   			
	   		}
	       };*/
		//这里的Handler实例将配合下面的Timer实例，完成定时更新图表的功能
		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {//处理消息的方法
				//刷新图表
				updateChart();
				super.handleMessage(msg);
			}
		};
		handler2= new Handler() {
			@Override
			public void handleMessage(Message msg) {//处理消息的方法
				wakeLock.release();
				camera.setPreviewCallback(null);
				camera.stopPreview();
				camera.release();//停止相机预览
				task.cancel();//停止曲线图绘制
				
				//获取当前时间
				Date date=new Date();
				DateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				final String time=format.format(date);
				final String rate=String.valueOf(beatsdata);
				//定义一个AlertDialog.Builder对象
				final android.app.AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
				builder.setTitle("结果");
				builder.setMessage("当前时间："+time+"\n"+"您的心率："+rate+"次/分");
				builder.setPositiveButton("确定"
						,new DialogInterface.OnClickListener() {			
							@Override
							public void onClick(DialogInterface dialog, int which) {
								
							}
						});
				builder.create().show();
			}
		};
		
		task = new TimerTask() {
			@Override
			public void run() {
				Message message = new Message();
				message.what = 1;
				handler.sendMessage(message);//发送消息
			}
		};
		task1=new TimerTask(){//最后停止计算的任务，然后获取到心率值，展示出来
			@Override
			public void run(){
				Message message = new Message();
				message.what = 1;
				handler2.sendMessage(message);//发送消息	
			}
		};
		//安排计时器周期任务
		timer.schedule(task,1,20);           //定时刷新曲线,延时1毫秒后开始重复执行task的run方法，周期是20毫秒间隔
		timer.schedule(task1,20000);	//设置停止的时间
		//获取摄像头
		preview = (SurfaceView) findViewById(R.id.preview);
		previewHolder = preview.getHolder();//接口，监听器
		previewHolder.addCallback(surfaceCallback);//添加callback接口，有三个标准方法监听图像变化，在changed中实时绘制图像
		previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		text = (TextView) findViewById(R.id.text);
		text1 = (TextView) findViewById(R.id.text1);
		text2 = (TextView) findViewById(R.id.text2);
		
		//背景灯开启
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);//电源状态
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
	}

	
	@Override
	public void onDestroy() {
		//当结束程序时关掉Timer
		timer.cancel();
		super.onDestroy();
	};


	protected XYMultipleSeriesRenderer buildRenderer(int color, PointStyle style, boolean fill) {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();//获取渲染器

		//设置图表中曲线本身的样式，包括颜色、点的大小以及线的粗细等
		XYSeriesRenderer r = new XYSeriesRenderer();
		r.setColor(Color.RED);
//		r.setPointStyle(null);
//		r.setFillPoints(fill);
		r.setLineWidth(1);//线条的宽度
		renderer.addSeriesRenderer(r);
		return renderer;
	}

	protected void setChartSettings(XYMultipleSeriesRenderer renderer, String xTitle, String yTitle,
			double xMin, double xMax, double yMin, double yMax, int axesColor, int labelsColor) {
		//有关对图表的渲染可参看api文档
		renderer.setChartTitle(title);
		renderer.setXTitle(xTitle);
		renderer.setYTitle(yTitle);
		renderer.setXAxisMin(xMin);//x最少0个点
		renderer.setXAxisMax(xMax);//x最少300
		renderer.setYAxisMin(yMin);//y最少4个
		renderer.setYAxisMax(yMax);//y最多16个点
		renderer.setAxesColor(axesColor);//轴线颜色
		renderer.setLabelsColor(labelsColor);//标签颜色
		renderer.setShowGrid(true);//是否显示网格
		renderer.setGridColor(Color.GREEN);//网格颜色
		renderer.setXLabels(20);//x轴刻度
		renderer.setYLabels(10);
		renderer.setXTitle("Time");
		renderer.setYTitle("mmHg");
		renderer.setYLabelsAlign(Align.RIGHT);
		renderer.setPointSize((float) 3 );
		renderer.setShowLegend(false);//是否显示文字
	}

	private void updateChart() {

		//设置好下一个需要增加的节点
		//    	addX = 10;
		//addY = (int)(Math.random() * 90 + 50);  
		//		addY = (int)(HardwareControler.readADC());
		//    	addY=10+addY;
		//    	if(addY>1400)
		//    		addY=10;
		if(flag==1)			//如果flag一直为1，说明是一条在y为10的直线
			addY=10;
		else{				//flag在获取跳动信息后修改为零，说明折线图需要改变成跳动了
			flag=1;
			if(gx<200)      //gx为decodeYUV420SPtoRedAvg返回值的拷贝，小于两百说明手指没有在摄像头上，红色素少
			{
				if(hua[20]>1)
				{
					Toast.makeText(MainActivity.this, "请用您的指尖盖住摄像头镜头！", Toast.LENGTH_SHORT).show();
					hua[20]=0;
					}
				hua[20]++;
				return;
			}
			else
			hua[20]=10;
			j=0;
			
		}
		if(j<20){
			addY=hua[j]; //20个点是hua中的长度，20个点之后说明绘制了一整个跳动图像了
			j++;
		}
			
		//移除数据集中旧的点集
		mDataset.removeSeries(series);

		//判断当前点集中到底有多少点，因为设置x轴总共只能容纳300个，所以当点数超过300时，长度永远是300
		int length = series.getItemCount();
		int bz=0;
		//		addX = length;
		if (length > 300) {//不过300，直接绘制，过了300，需要将x减去1，才可以看到绘制，才有移动的效果
			length = 300;
			bz=1;
		}
		addX = length;
		//将旧的点集中x和y的数值取出来放入backup中，并且将x的值加1，造成曲线向右平移的效果
		for (int i = 0; i < length; i++) {
			xv[i] = (int) series.getX(i) -bz;
			yv[i] = (int) series.getY(i);
		}

		//点集先清空，为了做成新的点集而准备
		series.clear();
		mDataset.addSeries(series);
		//将新产生的点首先加入到点集中，然后在循环体中将坐标变换后的一系列点都重新加入到点集中
		//这里可以试验一下把顺序颠倒过来是什么效果，即先运行循环体，再添加新产生的点
		
		series.add(addX, addY);//addx从length开始，造成连续的效果
		
		for (int k = 0; k < length; k++) {
			series.add(xv[k], yv[k]);//将剩余点添加进去
		}


		//在数据集中添加新的点集
		//		mDataset.addSeries(series);

		//视图更新，没有这一步，曲线不会呈现动态
		//如果在非UI主线程中，需要调用postInvalidate()，具体参考api
		chart.invalidate();
	}                                                                           //曲线


	@Override
	public void onConfigurationChanged(Configuration newConfig) {//解决数据丢失，没用到
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onResume() {
		super.onResume();
		wakeLock.acquire();
		camera = Camera.open();
		startTime = System.currentTimeMillis();
	}

	@Override
	public void onPause() {
		super.onPause();
		wakeLock.release();//停止闪光灯，停止摄像头
		camera.setPreviewCallback(null);
		camera.stopPreview();
		camera.release();//停止预览
		camera = null;
	}

	private static PreviewCallback previewCallback = new PreviewCallback() {
		//当存在预览帧(preview frame)时调用onPreviewFrame(),以便可以操作分析每一个预览帧
		//data-- a byte array of the picture data,图像内容字节数组
		public void onPreviewFrame(byte[] data, Camera cam) {
			if (data == null)
				throw new NullPointerException();
			//通过parameters获取图像的大小参数
			Camera.Size size = cam.getParameters().getPreviewSize();
			if (size == null)
				throw new NullPointerException();
			if (!processing.compareAndSet(false, true))
				return;
			int width = size.width;
			int height = size.height;
			//图像处理
			int imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(),height,width);
			gx=imgAvg;//记录下来有用，用来刷新折线图
			text1.setText("平均像素值是"+String.valueOf(imgAvg));
			//像素平均值imgAvg,日志
			//			Log.i(TAG, "imgAvg=" + imgAvg);
			if (imgAvg == 0 || imgAvg == 255) {//像素值错误
				processing.set(false);
				return;
			}

			int averageArrayAvg = 0;//前四次红色像素值的和
			int averageArrayCnt = 0;//次数，用来求平均值
			for (int i = 0; i < averageArray.length; i++) {//长度为四，刚开始均为0
				if (averageArray[i] > 0) {
					averageArrayAvg += averageArray[i];//
					averageArrayCnt++;
				}
			}

			int rollingAverage = (averageArrayCnt > 0)?(averageArrayAvg/averageArrayCnt):0;//计算四次获取到的像素的平均值
			TYPE newType = currentType;//刚开始为GREEN
			if (imgAvg < rollingAverage) {
				newType = TYPE.RED;//如果此时的平均像素小于前4次的额平均像素，则说明此时的红色值较小，颜色偏暗
				if (newType != currentType) {//条件成立时，说明newtype为RED，currentType为GREEN
					beats++;//跳动次数，也就是图像需要绘制的峰数
					flag=0;//全局变量，用来标记图像绘制的,updatechart中用到，如果等于0，说明需要绘制新的图像，旧图像为一条横线
					text2.setText("脉冲数是               "+String.valueOf(beats));
					//					Log.e(TAG, "BEAT!! beats=" + beats);
				}
			} else if (imgAvg > rollingAverage) {//如果图像颜色未变暗，继续测量
				newType = TYPE.GREEN;
			}

			if (averageIndex == averageArraySize)//anerage初始化为0，每四次清零，保证像素每四次循环放入
				averageIndex = 0;
			averageArray[averageIndex] = imgAvg;
			averageIndex++;//计算一次加一

			// Transitioned from one state to another to the same
			if (newType != currentType) {
				currentType = newType;//重新刷新，以便下一次继续计算
				//				image.postInvalidate();
			}
//获取当前时间（ms），以便跟开始时间计算时间差
			long endTime = System.currentTimeMillis();
			double totalTimeInSecs = (endTime - startTime) / 1000d;//计算出间隔的秒数，精确到小数位
			if (totalTimeInSecs >= 2) {//20ms间隔，2s就是取值了判断了100次
				double bps = (beats / totalTimeInSecs);//计算每秒的心跳数目
				int dpm = (int) (bps * 60d);//每分钟的心跳数目
				if (dpm < 30 || dpm > 180||imgAvg<200) {//如果心跳小于30或者大于180或者像素格式不纯，说明取值不正确，重新开始计算
					//获取系统当前时间（ms），重新开始计算
					startTime = System.currentTimeMillis();
					//beats心跳总数
					beats = 0;//心跳重新计算
					processing.set(false);//初始化重置
					return;
				}
				//				Log.e(TAG, "totalTimeInSecs=" + totalTimeInSecs + " beats="+ beats);
				if (beatsIndex == beatsArraySize)//beatsIndex，同像素四次计算后覆盖再记录一样
					beatsIndex = 0;
				beatsArray[beatsIndex] = dpm;
				beatsIndex++;
				int beatsArrayAvg = 0;
				int beatsArrayCnt = 0;
				for (int i = 0; i < beatsArray.length; i++) {
					if (beatsArray[i] > 0) {
						beatsArrayAvg += beatsArray[i];
						beatsArrayCnt++;
					}
				}
				int beatsAvg = (beatsArrayAvg / beatsArrayCnt);
				beatsdata=beatsAvg;//用来在对话框显示
				text.setText("您的的心率是"+String.valueOf(beatsAvg)+"  zhi:"+String.valueOf(beatsArray.length)
						+"    "+String.valueOf(beatsIndex)+"    "+String.valueOf(beatsArrayAvg)+"    "+String.valueOf(beatsArrayCnt));
//获取系统时间（ms）
				startTime = System.currentTimeMillis();
				beats = 0;
			}
			processing.set(false);
		}
	};
	//SurfaceHolder显示面的抽象接口
	private static SurfaceHolder.Callback surfaceCallback= new SurfaceHolder.Callback() {

		public void surfaceCreated(SurfaceHolder holder) {
			try {
				//将camera连接到一个SurfaceView，准备实时预览
				camera.setPreviewDisplay(previewHolder);
				camera.setPreviewCallback(previewCallback);
				//重点，给相机图像改变添加图像改变的操作。
				//获取红色素平均值并且判断心跳
				//每一个预览帧改变时回调，采用previewcallback之前的设置。
			} catch (Throwable t) {
				//Log.e("PreviewDemo-surfaceCallback","Exception in setPreviewDisplay()", t);
			}
		}
		//实时监控图像变化 (format or size)  format像素格式，width_height表示surface新的宽高
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			//获取摄像头参数
			Camera.Parameters parameters = camera.getParameters();
			parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//开启闪光灯
			Camera.Size size = getSmallestPreviewSize(width, height, parameters);
			if (size != null) {
				parameters.setPreviewSize(size.width, size.height);
				//Log.d(TAG, "Using width=" + size.width + " height="	+ size.height);
			}
			camera.setParameters(parameters);
			camera.startPreview();//绘制图像
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			// Ignore
		}
	};

	private static Camera.Size getSmallestPreviewSize(int width, int height,
			Camera.Parameters parameters) {
		Camera.Size result = null;
		for (Camera.Size size : parameters.getSupportedPreviewSizes()) {//获取预览的分辨率
			if (size.width <= width && size.height <= height) {//预览的分辨率与surface比较
				if (result == null) {
					result = size;
				} else {
					int resultArea = result.width * result.height;//预览分辨率与相机分辨率比较
					int newArea = size.width * size.height;
					if (newArea < resultArea)
						result = size;
				}
			}
		}
		return result;
	}
}