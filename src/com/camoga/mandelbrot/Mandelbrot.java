package com.camoga.mandelbrot;

import static org.jocl.CL.*;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JFrame;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

public class Mandelbrot {
	
	cl_context context;
	cl_command_queue command_queue;
	cl_device_id device;
	cl_program program;
	cl_kernel kernel;
	cl_mem[] memObjects = new cl_mem[3];
	
	int[] iters;
	double[] size;
	int[] dim;
	Pointer _iters;
	Pointer _size;
	Pointer _dim;
	
	JFrame f;
	Canvas c;
	
	int WIDTH = 1000, HEIGHT = 1000, iterations = 16384;
	BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
	int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	int[] colors = new int[iterations];
	
	public Mandelbrot() {
		// [-0.13330332482414903, -0.9920402512318591, 3.647082635893639E-14, 2.4202861936828413E-14]
		// [-0.0728599757211034, -0.9700688293180779, 3.4958147487884617E-14, 5.6066262743570405E-14]
		init();
		initGUI();
		
		for(int i = 0; i < colors.length-1; i++) {
			colors[i] = 0xff000000+Color.HSBtoRGB(i/512f, 1f, i/(i+10f));
		}
		
		colors[colors.length-1] = 0xff000000;
		
		drawMandelbrot();
	}
	
	Point start, end;
	boolean dragging = false;
	
	public void initGUI() {
		f = new JFrame("Mandelbrot");
		c = new Canvas();
		
		f.setSize(WIDTH, HEIGHT);
		f.setLocationRelativeTo(null);
		f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				clean();
				System.exit(0);
			}
		});
		
		MouseAdapter listener = new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				start = e.getPoint();
			}
			
			public void mouseReleased(MouseEvent e) {
				end = e.getPoint();
				if(dragging) {
					double xs = start.getX()/(double)WIDTH*size[2]+size[0];
					double ys = start.getY()/(double)HEIGHT*size[3]+size[1];

					double xe = end.getX()/(double)WIDTH*size[2]+size[0];
					double ye = end.getY()/(double)HEIGHT*size[3]+size[1];
					double width = xe-xs;
					double height = ye-ys;
					loadCoordinates(xs,ys,width,height);
					drawMandelbrot();
				}
				dragging = false;
			}
			
			public void mouseDragged(MouseEvent e) {
				dragging = true;
				end = e.getPoint();
			}
		};

		c.addMouseListener(listener);
		c.addMouseMotionListener(listener);
		f.add(c);
		f.setResizable(true);
		f.setVisible(true);
		c.createBufferStrategy(3);
		
		new Thread(() -> {
			while(true) {
				render();
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		},"Render").start();
		
	}
	
	public void init() {
		createContext(0);
		program = clCreateProgramWithSource(context, 1, new String[] {loadSrc("/mandelbrot.cl")}, null, null);
		clBuildProgram(program, 0, null, null, null, null);
		int n = WIDTH*HEIGHT;
		iters = new int[n];
		size = new double[4];
		dim = new int[] {WIDTH, HEIGHT, iterations-1};
		
		_size = Pointer.to(size);
		_dim = Pointer.to(dim);
		_iters = Pointer.to(iters);

		memObjects[0] = clCreateBuffer(context, CL_MEM_READ_ONLY, size.length*Sizeof.cl_double, _size, null);
		memObjects[1] = clCreateBuffer(context, CL_MEM_READ_ONLY, dim.length*Sizeof.cl_int, _dim, null);
		memObjects[2] = clCreateBuffer(context, CL_MEM_READ_WRITE, iters.length*Sizeof.cl_int, _iters, null);
		
		kernel = clCreateKernel(program, "mandelbrot", null);

		clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memObjects[0]));
		clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memObjects[1]));
		clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(memObjects[2]));
		
		loadCoordinates(-2,-2,4,4);
		clEnqueueWriteBuffer(command_queue, memObjects[1], CL_TRUE, 0, dim.length*Sizeof.cl_int, _dim, 0, null, null);
	}
	
	public void loadCoordinates(double xs, double ys, double width, double height) {
		size[0] = xs;
		size[1] = ys;
		size[2] = width;
		size[3] = height;
		System.out.println(Arrays.toString(size));
		clEnqueueWriteBuffer(command_queue, memObjects[0], CL_TRUE, 0, size.length*Sizeof.cl_double, _size, 0, null, null);
	}
	
	public void drawMandelbrot() {
		clEnqueueNDRangeKernel(command_queue, kernel, 2, null, new long[] {WIDTH,HEIGHT}, new long[] {50,20}, 0, null, null);
//		byte[] buffer = new byte[200];
//		clGetDeviceInfo(device, CL_DEVICE_AVAILABLE, buffer.length, Pointer.to(buffer), new long[] {10});
//		System.out.println(Arrays.toString(buffer));
		clEnqueueReadBuffer(command_queue, memObjects[2], CL_TRUE, 0, iters.length*Sizeof.cl_int, _iters, 0, null, null);
		
		for(int i = 0; i < pixels.length; i++) {
			pixels[i] = colors[iters[i]];
		}
	}
	
	public void render() {
		Graphics g = c.getBufferStrategy().getDrawGraphics();
		
		g.drawImage(image, 0, 0, null);
		if(dragging) {
			g.setColor(Color.red);
			g.drawRect(start.x, start.y, end.x-start.x, end.y-start.y);
		}
		g.dispose();
		c.getBufferStrategy().show();
	}
	
	public static void main(String[] args) {
		new Mandelbrot();
	}
	
	public void clean() {
		for(int a = 0; a < memObjects.length; a++) {
			clReleaseMemObject(memObjects[a]);
		}
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		clReleaseCommandQueue(command_queue);
		clReleaseContext(context);
	}
	
	public String loadSrc(String file) {
		try {
			return new String(getClass().getResourceAsStream(file).readAllBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public void createContext(int dev) {
		final int platformIndex = dev == 0 ? 0:1;
        final long deviceType = dev == 2 ? CL_DEVICE_TYPE_CPU:CL_DEVICE_TYPE_GPU;
        final int deviceIndex = 0;
		
        CL.setExceptionsEnabled(true);
        
		int numPlatformsArray[] = new int[1];
		clGetPlatformIDs(0, null, numPlatformsArray);
		int numPlatforms = numPlatformsArray[0];
		
		cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
		clGetPlatformIDs(platforms.length, platforms, null);
		cl_platform_id platform = platforms[platformIndex];
		
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
		
		
		int[] numDevicesArray = new int[1];
		clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
		int numDevices = numDevicesArray[0];
		
		cl_device_id[] devices = new cl_device_id[numDevices];
		clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
		device = devices[deviceIndex];
		
		long[] length = new long[1];
		byte[] buffer = new byte[2000];
		clGetDeviceInfo(device, CL_DEVICE_NAME, 2000, Pointer.to(buffer), length);
		System.out.println(new String(buffer).trim());
		context = clCreateContext(contextProperties, 1, new cl_device_id[] {device}, null, null, null);
		command_queue = clCreateCommandQueue(context, device, 0, null);
	}
}
