__kernel void mandelbrot(__global double *coords, __global int *dim, __global int *iters) {
	int i = get_global_id(0);
	int j = get_global_id(1);
	
	double xc = coords[0] + coords[2]/dim[0]*i;
	double yc = coords[1] + coords[3]/dim[1]*j;
	
	double x = 0;
	double y = 0;
	
	double xx = 0;
	double yy = 0;
	
	int it = 0;
	
	for(; it < dim[2]; it++) {
		if(xx+yy > 4) break;
		y = y*x;
		y += y + yc;
		x = xx-yy+xc;
		xx = x*x;
		yy = y*y;
	}
	
	iters[i+j*dim[0]] = it;
}