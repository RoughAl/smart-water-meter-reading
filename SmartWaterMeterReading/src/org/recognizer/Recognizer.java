/**
 * Copyright (C) 2013 pauline ruegg-reymond
 * <pauline.ruegg.reymond@gmail.com>
 * eauservice
 * rue de Gen�ve 36
 * case postale 7416
 * CH-1002 Lausanne
 * 
 * This file is part of SmartWaterMeterReading
 * 
 * SmartWaterMeterReading is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SmartWaterMeterReading is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.recognizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.encog.mathutil.matrices.Matrix;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.som.SOM;
import org.encog.neural.som.training.basic.BestMatchingUnit;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.Blitter;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * This class contains methods corresponding to different steps of the image processing to get a meter index from a meter picture.
 * 
 * @author pauline ruegg-reymond
 *
 */
public class Recognizer {
	
	private int MIDDLE = -1;
	private int TENTHOUSANDTH = -1;
	private int TENTH = -1;
	private int THOUSANDTH = -1;
	private int HUNDREDTH = -1;
	
	
	public int getMIDDLE() {
		return MIDDLE;
	}



	public int getTENTHOUSANDTH() {
		return TENTHOUSANDTH;
	}



	public int getTENTH() {
		return TENTH;
	}



	public int getTHOUSANDTH() {
		return THOUSANDTH;
	}



	public int getHUNDREDTH() {
		return HUNDREDTH;
	}



	/** Searches for red pixels.
	 * @param ip - the image to process.
	 * @return A binary image with red pixels set to 255 and non red ones to 0.
	 */
	public ImageProcessor findRed(ImageProcessor ip) {
		if(ip.isGrayscale()) {
			return null;
		}
		
		ImageStack is = ((ColorProcessor) ip).getHSBStack();
		ImageProcessor ip2 = is.getProcessor(1);
		float[][] h = ip2.getFloatArray();
		ip2 = is.getProcessor(2);
		float[][] s = ip2.getFloatArray();
		ip2 = is.getProcessor(3);
		float[][] v = ip2.getFloatArray();
		
		for(int x=0;x<ip.getWidth();x++) {
			for(int y=0;y<ip.getHeight();y++) {
				h[x][y] /= 255.0;
				s[x][y] /= 255.0;
				v[x][y] /= 255.0;
				double pix = h[x][y] - 0.68;
				pix = Math.max(pix, 0.035 - h[x][y]);
				double ms = 0.13;
				pix = Math.min(pix, s[x][y] - ms);
				double mv1 = 0.35, mv2 = 0.7;
				double p = (mv1 - mv2)/(1 - ms);
				pix = Math.min(pix, v[x][y] - (mv1 - p) - p*s[x][y]);
				double tmp = 0.06 - h[x][y];
				tmp = Math.min(tmp, s[x][y] - 0.85);
				tmp = Math.min(tmp,  v[x][y] - 0.75);
				pix = Math.max(pix,  tmp);
				if (pix > 0.001) {
					pix = 1.0;
				}
				
				ip2.putPixelValue(x, y, pix*255);
			}
		}
		
		ip2 = ip2.convertToByte(false);
		ip2.autoThreshold();
		return ip2;
	}

	
	
	/**	Finds and measures blobs on a binary image.
	 * @param ip - the image to process.
	 * @param minArea - the minimal area a blob must have to be analyzed.
	 * @return A table describing the blobs of the image. Measured properties are area, centroid and bounding box.
	 */
	public ResultsTable findBlob(ImageProcessor ip, int minArea) {
		ImageProcessor ip2 = (ImageProcessor) ip.clone();
		ip2 = ip2.convertToByte(true);
		ip2.invertLut();
		ip2.setBackgroundValue(0);
		ImagePlus im = new ImagePlus();
		im.setProcessor(ip2);
		
		ResultsTable rt = new ResultsTable();
		ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.SHOW_NONE,
				Measurements.AREA + Measurements.CENTROID + Measurements.RECT,
				rt, minArea, Double.MAX_VALUE);
		
		pa.analyze(im);
		return rt;
	}
	
	
	
	/** Identifies the blobs and finds their center.
	 * Attributes numbers from 0 to 4 to MIDDLE, TENTHOUSANDTH, TENTH, THOUSANDTH and HUNDREDTH. These numbers corresponds to the line of the
	 * blob in the ResultsTable.
	 * @param ip - the image to process.
	 * @param blobs - table describing blobs of the image to process. Can be obtained unsing method findBlobs.
	 * @return A table describing the blobs with two additional columns : X_CENTER_OF_MASS and Y_CENTER_OF_MASS. These columns store x and y coordinates
	 * of the center of the button.
	 */
	public ResultsTable treatBlobs(ImageProcessor ip, ResultsTable blobs) {
		if(blobs.getCounter()!=5) {
			throw new IllegalArgumentException("Wrong number of blobs.");
		}
		
		if(!blobs.columnExists(ResultsTable.AREA) || !blobs.columnExists(ResultsTable.X_CENTROID) ||
				!blobs.columnExists(ResultsTable.Y_CENTROID) || !blobs.columnExists(ResultsTable.ROI_X) ||
				!blobs.columnExists(ResultsTable.ROI_Y) || !blobs.columnExists(ResultsTable.ROI_HEIGHT) ||
				!blobs.columnExists(ResultsTable.ROI_WIDTH)) {
			throw new IllegalArgumentException("Data of blobs do not exist.");
		}
		
		float[] dist = new float[10];
		float[] dist2 = new float[10];
		int[][] correspondingIndex = {{0,1}, {0,2}, {0,3}, {0,4}, {1,2}, {1,3}, {1,4}, {2,3}, {2,4}, {3,4}};
		
		float[] centroid_x = blobs.getColumn(ResultsTable.AREA);
		MIDDLE = Searcher.findMax(centroid_x);
		
		centroid_x = blobs.getColumn(ResultsTable.X_CENTROID);
		float[] centroid_y = blobs.getColumn(ResultsTable.Y_CENTROID);
		int tmp = 0;
		for(int i=0;i<4;i++) {
			for(int j=i+1;j<5;j++) {
				dist[tmp] = (centroid_x[i] - centroid_x[j])*(centroid_x[i] - centroid_x[j]);
				dist[tmp] += (centroid_y[i] - centroid_y[j])*(centroid_y[i] - centroid_y[j]);
				dist2[tmp] = dist[tmp];
				if (correspondingIndex[tmp][0] == MIDDLE || correspondingIndex[tmp][1] == MIDDLE) dist2[tmp] = 0;
				tmp++;
			}
		}
		
		tmp = Searcher.findMax(dist2);
		int[] indices = correspondingIndex[tmp];
		int[] tmp1 = {indices[0], MIDDLE};
		Arrays.sort(tmp1);
		int[] tmp2 = {indices[1], MIDDLE};
		Arrays.sort(tmp2);
		int key1 = Searcher.search(correspondingIndex, tmp1);
		int key2 = Searcher.search(correspondingIndex, tmp2);
		if (dist[key1] < dist[key2]) {
			TENTHOUSANDTH = indices[0];
			TENTH = indices[1];
		} else{
			TENTHOUSANDTH = indices[1];
			TENTH = indices[0];
		}
		
		for (int i=0;i<10;i++) {
			dist2[i] = dist[i];
			if ((correspondingIndex[i][0] != TENTHOUSANDTH && correspondingIndex[i][1] != TENTHOUSANDTH) || correspondingIndex[i][0]==MIDDLE || correspondingIndex[i][1]==MIDDLE || correspondingIndex[i][0]==TENTH || correspondingIndex[i][1]==TENTH) {
				dist2[i] = Float.MAX_VALUE;
			}
		}
		tmp = Searcher.findMin(dist2);
		if (correspondingIndex[tmp][0] == TENTHOUSANDTH) {
			THOUSANDTH = correspondingIndex[tmp][1];
		} else THOUSANDTH = correspondingIndex[tmp][0];
		
		int[] res = {MIDDLE, TENTHOUSANDTH, TENTH, THOUSANDTH, HUNDREDTH};
		for (int i=0;i<5;i++) {
			if (Searcher.search(res, i) >= 0) {
				continue;
			} else {
				HUNDREDTH = i;
				break;
			}
		}
		
		for (int i=0;i<5;i++) {
			if(i == MIDDLE) {
				double x = blobs.getValueAsDouble(ResultsTable.X_CENTROID, i);
				double y = blobs.getValueAsDouble(ResultsTable.Y_CENTROID, i);
				blobs.setValue(ResultsTable.X_CENTER_OF_MASS, i,  x);
				blobs.setValue(ResultsTable.Y_CENTER_OF_MASS, i,  y);
			}
			else {
				double x =  blobs.getValueAsDouble(ResultsTable.ROI_X, i);
				double y = blobs.getValueAsDouble(ResultsTable.ROI_Y, i);
				double width =  blobs.getValueAsDouble(ResultsTable.ROI_WIDTH, i);
				double height =  blobs.getValueAsDouble(ResultsTable.ROI_HEIGHT, i);
				
				double rap = width/height;
				
				if (rap <= 0.5) {
					//aiguille plus ou moins verticale
					
					int[] scan = Tools.numOfPixPerRow(ip, (int)x, (int)y, (int)width, (int)height);
					int[] tmpdat = Searcher.findAllMax(scan);
					double j = (Searcher.max(tmpdat) + Searcher.min(tmpdat))/2.0;
					blobs.setValue(ResultsTable.X_CENTER_OF_MASS, i,  x+width/2.0);
					blobs.setValue(ResultsTable.Y_CENTER_OF_MASS, i,  y+j);
				} else if (rap >= 2) {
					//aiguille plus ou moins horizontale
					
					int[] scan = Tools.numOfPixPerCol(ip, (int)x, (int)y, (int)width, (int)height);
					int[] tmpdat = Searcher.findAllMax(scan);
					double j = (Searcher.max(tmpdat) + Searcher.min(tmpdat))/2.0;
					blobs.setValue(ResultsTable.X_CENTER_OF_MASS, i, x+j);
					blobs.setValue(ResultsTable.Y_CENTER_OF_MASS, i,  y+height/2.0);
				} else {
					//aiguille oblique
					
					int[] scan1 = Tools.numOfPixPerLine(ip, (int)x, (int)y, (int)width, (int)height, 45.0);
					int[] scan2 = Tools.numOfPixPerLine(ip, (int)x, (int)y, (int)width, (int)height, -45.0);
					double j, k;
					if (Searcher.numOf(scan1,0) > Searcher.numOf(scan2,0)) {
						int[] jarray = Searcher.findAllMax(scan2);
						if (jarray.length > 1) {
							j = (Searcher.min(jarray) + Searcher.max(jarray))/2.0;
						} else j = jarray[0];
						j *= height/scan2.length;
						k = width - rap*j;
					} else {
						int[] jarray = Searcher.findAllMax(scan1);
						if (jarray.length>1) {
							j = (Searcher.min(jarray)+Searcher.max(jarray))/2.0;
						} else j = jarray[0];
						j *= height/scan1.length;
						k = j*rap;
					}
					blobs.setValue(ResultsTable.X_CENTER_OF_MASS, i, x+k);
					blobs.setValue(ResultsTable.Y_CENTER_OF_MASS, i, y+j);
				}
			}
		}
		return blobs;
	}
	
	
	
	/**	Gets the number dial in a water meter picture.
	 * @param ip - the image to process.
	 * @param blobs - table containing data about some interest points. Can be obtained using method treatBlobs.
	 * @return An image of size DIAL_WIDTH x DIAL_HEIGHT representing the number dial.
	 */
	public ImageProcessor getDial(ImageProcessor ip, ResultsTable blobs, TypeSpec type) {
		if(blobs.getCounter()!=5) {
			throw new IllegalArgumentException("Wrong number of interest points.");
		}
		if(!blobs.columnExists(ResultsTable.X_CENTER_OF_MASS) || !blobs.columnExists(ResultsTable.Y_CENTER_OF_MASS)) {
			throw new IllegalArgumentException("Coordinates of interest points do not exist.");
		}
		if(MIDDLE < 0 || TENTHOUSANDTH < 0 || TENTH < 0 || THOUSANDTH < 0 || HUNDREDTH < 0) {
			throw new IllegalArgumentException("Interest points are not identified.");
		}
		
		/* TODO
		 * Find a good method: MLS_AFFINE might be really efficient if there is enough interest
		 * points (see what can be found with surf) but this is not satisfactory with only the five red buttons.
		 * AFFINE_2D only uses 3 out of the 5 red buttons and is obviously not robust against image deformation
		 * due to a bad angle when taking the picture. Used because there is no better method so far.
		 * Other implemented methods don't give satisfactory results.
		 */
		
		double[][] p = {{type.getMiddleX(), type.getMiddleY()},
				{type.getHundredthX(), type.getHundredthY()},
				{type.getTenthousandthX(), type.getTenthousandthY()},
				{type.getTenthX(), type.getTenthY()},
				{type.getThousandthX(), type.getThousandthY()}};
		double[][] q = {{blobs.getValueAsDouble(ResultsTable.X_CENTER_OF_MASS, MIDDLE), blobs.getValueAsDouble(ResultsTable.Y_CENTER_OF_MASS, MIDDLE)},
				{blobs.getValueAsDouble(ResultsTable.X_CENTER_OF_MASS, HUNDREDTH), blobs.getValueAsDouble(ResultsTable.Y_CENTER_OF_MASS, HUNDREDTH)},
				{blobs.getValueAsDouble(ResultsTable.X_CENTER_OF_MASS, TENTHOUSANDTH), blobs.getValueAsDouble(ResultsTable.Y_CENTER_OF_MASS, TENTHOUSANDTH)},
				{blobs.getValueAsDouble(ResultsTable.X_CENTER_OF_MASS, TENTH), blobs.getValueAsDouble(ResultsTable.Y_CENTER_OF_MASS, TENTH)},
				{blobs.getValueAsDouble(ResultsTable.X_CENTER_OF_MASS, THOUSANDTH), blobs.getValueAsDouble(ResultsTable.Y_CENTER_OF_MASS, THOUSANDTH)}};
		
		
		int flag = Tools.AFFINE_2D;
		
		double[] topleft = {type.getDialX(), type.getDialY()};
		topleft = Tools.correspondingPoint(p, q, topleft, flag);
		
		double[] topright = {type.getDialX() + type.getDialWidth(),  type.getDialY()};
		topright = Tools.correspondingPoint(p, q, topright, flag);
		
		double[] bottomleft = {type.getDialX(), type.getDialY() + type.getDialHeight()};
		bottomleft = Tools.correspondingPoint(p, q, bottomleft, flag);
		
		double[] bottomright = {type.getDialX() + type.getDialWidth(), type.getDialY() + type.getDialHeight()};
		bottomright = Tools.correspondingPoint(p, q, bottomright, flag);
		
		double[] q0 = {0,0}, q1 = {type.getDialWidth(),0}, q2 = {0,type.getDialHeight()}, q3 = {type.getDialWidth(), type.getDialHeight()};
		ImageProcessor ip2 = Tools.perspective(ip, topleft, topright, bottomleft, bottomright, q0, q1, q2, q3);
		
		return ip2;
	}
	
	
	
	/** Transforms the image to black and white.
	 * @param ip - the image to process.
	 * @return An image in black and white.
	 */
	public ImageProcessor binarize(ImageProcessor ip) {
		/* TODO
		 * A true function.
		 */
		ImageProcessor ip2 = (ImageProcessor) ip.clone();
		
		ip2 = ip2.convertToByte(true);
		ip2.threshold(120);
		
		return ip2;
	}
	
	
	
	/** Sorts blobs from left to right and groups them and sorts them form up to down if they are aligned vertically.
	 * @param blobs - blobs to sort.
	 * @return A list of indices sorted in preferred order.
	 */
	public List<int[]> preferredOrder(ResultsTable blobs) {
		List<int[]> res = new ArrayList<int[]>();
		int[] order = Searcher.order(blobs.getColumnAsDoubles(ResultsTable.ROI_X));
		boolean[] overlapping = Tools.horizontalOverlap(blobs);
		for (int k=0;k<order.length;k++) {
			int i = order[k];
			if (k<order.length-1) {
				int j = order[k+1];
				if ((j<i && overlapping[j*order.length - (j+1)*j/2 + i-j-1]) || (j>i && overlapping[i*order.length - (i+1)*i/2 + j-i-1])) {
					int y = (int) blobs.getValueAsDouble(ResultsTable.ROI_Y, i);
					int height = (int) blobs.getValueAsDouble(ResultsTable.ROI_HEIGHT, i);
					int y2 = (int) blobs.getValueAsDouble(ResultsTable.ROI_Y, j);
					int height2 = (int) blobs.getValueAsDouble(ResultsTable.ROI_HEIGHT, j);
					if (y+height < y2) {
						int[] tmp = {i, j};
						res.add(tmp);
						k++;
						continue;
					} else if (y2+height2 < y) {
						int[] tmp = {j, i};
						res.add(tmp);
						k++;
						continue;
					}
				}
			}
			int[] tmp = {i};
			res.add(tmp);
		}
		return res;
	}
	
	
	
	/** Separates out characters.
	 * @param ip - image (preferably binary) containing a whole sentence or word.
	 * @param blobs - data about individual characters.
	 * @return An array of images, each one contains a single character.
	 */
	public ImageStack getCharacters(ImageProcessor ip, ResultsTable blobs, TypeSpec type, List<int[]> order) {
		ImageProcessor ip2 = (ImageProcessor) ip.clone();
		
		ImageStack res = new ImageStack(type.getCharWidth(), type.getCharHeight());
		
		for (int k=0;k<order.size();k++) {
			int i = order.get(k)[0];
			
			int x = (int) blobs.getValueAsDouble(ResultsTable.ROI_X, i);
			int y = (int) blobs.getValueAsDouble(ResultsTable.ROI_Y, i);
			int width = (int) blobs.getValueAsDouble(ResultsTable.ROI_WIDTH, i);
			int height = (int) blobs.getValueAsDouble(ResultsTable.ROI_HEIGHT, i);
			Roi bb = new Roi(x, y, width, height);
			ip2.setRoi(bb);
			ImageProcessor ip3 = ip2.crop();
			
			if (order.get(k).length == 1) {
				ip3 = ip3.resize(type.getCharWidth(), type.getCharHeight());
				res.addSlice(ip3);
				
			} else if (order.get(k).length == 2) {
				/* TODO
				 * trouver un moyen pour ne pas trop �largir les bas de 1 en redimensionnant l'image.
				 */
				int j = order.get(k)[1];
				int height2 = (int) blobs.getValueAsDouble(ResultsTable.ROI_HEIGHT, j);
				
				if (height < height2*0.1 || height2 < height*0.1) {
					if (height2>height) {
						height = height2;
						x = (int) blobs.getValueAsDouble(ResultsTable.ROI_X, j);
						y = (int) blobs.getValueAsDouble(ResultsTable.ROI_Y, j);
						width = (int) blobs.getValueAsDouble(ResultsTable.ROI_WIDTH, j);
						bb = new Roi(x, y, width, height);
						ip2.setRoi(bb);
						ip3 = ip2.crop();
					}
					ip3 = ip3.resize(type.getCharWidth(), type.getCharHeight());
					res.addSlice(ip3);
					
				} else {
					int x2 = (int) blobs.getValueAsDouble(ResultsTable.ROI_X, j);
					int y2 = (int) blobs.getValueAsDouble(ResultsTable.ROI_Y, j);
					int width2 = (int) blobs.getValueAsDouble(ResultsTable.ROI_WIDTH, j);
					bb = new Roi(x2, y2, width2, height2);
					ip2.setRoi(bb);
					ImageProcessor ip4 = ip2.crop();
					ImageProcessor ip5 = ip3.createProcessor(width, height+height2);
					ip5.setColor(0);
					ip5.fill();
					ip5.copyBits(ip3, 0, height2, Blitter.COPY);
					ip3 = ip5.duplicate();
					ip3 = ip3.resize(type.getCharWidth(), type.getCharHeight());
					res.addSlice(ip3);
					ip5 = ip4.createProcessor(width2, height+height2);
					ip5.setColor(0);
					ip5.fill();
					ip5.copyBits(ip4, 0, 0, Blitter.COPY);
					ip4 = ip5.duplicate();
					ip4 = ip4.resize(type.getCharWidth(), type.getCharHeight());
					res.addSlice(ip4);
				}
			}
		}
		
		return res;
	}
	
	
	
	/** Transforms an ImageStack into an MLDataSet. Each slice of the ImageStack becomes a distinct item of the MLDataSet.
	 * @param is
	 * @return
	 */
	public MLDataSet imageStack2MLDataSet(ImageStack is) {
		MLDataSet res = new BasicMLDataSet();
		for (int i=0;i<is.getSize();i++) {
			double[] data = new double[is.getWidth()*is.getHeight()];
			ImageProcessor ip = is.getProcessor(i+1);
			int j=0;
			for (int y=0;y<is.getHeight();y++) {
				for (int x=0;x<is.getWidth();x++) {
					data[j++]=ip.get(x,y);
				}
			}
			MLData mldata =  new BasicMLData(data);
			res.add(mldata);
		}
		return res;
	}
	
	
	
	/**
	 * Uses a SOM network to classify the images of numbers.
	 * @param charsDataSet - each item of this set is the image of a number to recognize (in the format needed by the SOM network)
	 * @param type - type of the water meter. This type contains a SOM network trained with images of digits of this specific type.
	 * @param order - list relating the order of the images in "charsDataSet" and the human reading order.
	 * @return an array containing the recognized numbers for all the image numbers in "charsDataSet".
	 */
	public int[] getNumbers(MLDataSet charsDataSet, TypeSpec type, List<int[]> order) {
		SOM net = type.getNet();
		
		BestMatchingUnit bmu = new BestMatchingUnit(net);
		Matrix W = net.getWeights();
		int L = (int) charsDataSet.getRecordCount();
		int[] res = new int[L];
		Iterator<MLDataPair> iter = charsDataSet.iterator();
		int i = 0;
		while (iter.hasNext()) {
			MLData input = iter.next().getInput();
			res[i] = net.winner(input);
			i++;
		}
		
		/* TODO trouver une m�thode plus intelligente pour forcer que deux
		 * chiffres align�s verticalement soient cons�cutifs
		 */
		i = 0;
		for (int k=0;k<order.size();k++) {
			if (order.get(k).length == 2) {
				int j = i+1;
				MLData input1 = charsDataSet.get(i).getInput();
				MLData input2 = charsDataSet.get(j).getInput();
				double dist11 = bmu.calculateEuclideanDistance(W, input1, res[i]);
				double dist12 = bmu.calculateEuclideanDistance(W,  input2, (res[i]+1)%10);
				double dist21 = bmu.calculateEuclideanDistance(W, input1, (res[j]+9)%10);
				double dist22 = bmu.calculateEuclideanDistance(W,  input2, res[j]);
				if (dist11+dist12 < dist21+dist22) {
					res[j] = (res[i]+1)%10;
				} else {
					res[i] = (res[j]+9)%10;
				}
				i++;
			}
			i++;
		}
		return res;
	}
	
	
	
	/**
	 * Similar as getNumbers() but returns also the distance of the recognized number to the model.
	 * @param charsDataSet
	 * @param type
	 * @param order
	 * @return
	 */
	public double[][] getNumbersAndDistances(MLDataSet charsDataSet, TypeSpec type, List<int[]> order) {
		SOM net = type.getNet();
		
		BestMatchingUnit bmu = new BestMatchingUnit(net);
		Matrix W = net.getWeights();
		int L = (int) charsDataSet.getRecordCount();
		double[][] res = new double[L][2];
		Iterator<MLDataPair> iter = charsDataSet.iterator();
		int i = 0;
		while (iter.hasNext()) {
			MLData input = iter.next().getInput();
			res[i][0] = net.winner(input);
			res[i][1] = bmu.calculateEuclideanDistance(W,input,(int) res[i][1]);
			i++;
		}

		i = 0;
		for (int k=0;k<order.size();k++) {
			if (order.get(k).length == 2) {
				int j = i+1;
				MLData input1 = charsDataSet.get(i).getInput();
				MLData input2 = charsDataSet.get(j).getInput();
				double dist11 = res[i][1];
				double dist12 = bmu.calculateEuclideanDistance(W,  input2, (int) ((res[i][0]+1)%10));
				double dist21 = bmu.calculateEuclideanDistance(W, input1, (int) ((res[j][0]+9)%10));
				double dist22 = res[j][1];
				if (dist11+dist12 < dist21+dist22) {
					res[j][0] = (res[i][0]+1)%10;
					res[j][1] = (dist11+dist12)/2;
				} else {
					res[i][0] = (res[j][0]+9)%10;
					res[i][1] = (dist21+dist22)/2;
				}
				i++;
			}
			i++;
		}
		return res;
	}
	
	
	
	/**
	 * Transforms the array of digits to an integer. The difficulty is to chose which digit we keep when there are two consecutive digits for the same number.
	 * @param numbers - array of numbers recognized on the original image.
	 * @param order - list relating the order of the numbers in the array "numbers" with the order of the items of the ResultsTable "blobs".
	 * @param is - Parameters of the images of the numbers on the original image. In particular, their height is useful.
	 * @return The index of the meter on the original picture.
	 */
	public int digits2int(int[] numbers, List<int[]> order, ResultsTable blobs) {
		if (numbers.length != blobs.getCounter()) return 0;
		int res=0;
		int L = order.size()-1;//The number of digits should be a parameter in the TypeSpec and there should be a verification somewhere that the right number was found.
		int j=0;
		for (int i=0;i<order.size();i++) {
			if (order.get(i).length == 1) {
				res += numbers[j]*(Math.pow(10, L));
			} else if (order.get(i).length == 2) {
				/*
				 * If there are two consecutive numbers for the same digit, we chose the number occupying the largest height on the picture. More accurate methods could be used, for example compare the resulting index with the previous index read for this meter.
				 */
				int k = order.get(i)[0];
				int h1 = (int) blobs.getValueAsDouble(ResultsTable.ROI_HEIGHT, k);
				k = order.get(i)[1];
				int h2 = (int) blobs.getValueAsDouble(ResultsTable.ROI_HEIGHT, k);
				if (h1>h2) {
					res += numbers[j]*(Math.pow(10, L));
				} else {
					res += numbers[j+1]*(Math.pow(10, L));
				}
				j++;
			}
			j++;
			L--;
		}
		return res;
	}
}
