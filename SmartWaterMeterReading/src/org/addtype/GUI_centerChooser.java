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

package org.addtype;

import java.awt.Choice;
import java.awt.Color;
import java.awt.event.MouseEvent;

import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.YesNoCancelDialog;
import ij.plugin.tool.PlugInTool;

/**
 * This class is a plug-in of ImageJ that allows the user to chose 5 points on the meter picture. It is not used in the current version of the program addtype.
 * 
 * @author pauline ruegg-reymond
 *
 */
public class GUI_centerChooser extends PlugInTool {
	private boolean next = false;
	private boolean canceled = false;
	private boolean[] alreadyChoosen = {false, false, false, false, false};
	private int[][] coordinates = new int[5][2];
	

	@Override
	public void mouseClicked(ImagePlus imp, MouseEvent e) {
		Overlay overlay = imp.getOverlay();
		if (overlay == null) {
			overlay = new Overlay();
			imp.setOverlay(overlay);
		}
		
		int x = e.getX();
		int y = e.getY();
		PointRoi point = new PointRoi(x,y);
		point.setName("tmp");
		overlay.add(point);
		overlay.setFillColor(Color.WHITE);
		overlay.drawBackgrounds(true);
		imp.updateAndDraw();
		GenericDialog gd = new GenericDialog("De quel bouton s'agit-il?");
		String[] labels = {"Dixi�mes", "Centi�mes", "Milli�mes", "Dix-milli�mes", "Milieu"};
		gd.addChoice("Bouton", labels, null);
		gd.showDialog();
		if (gd.wasOKed()){
			Choice choice = (Choice) gd.getChoices().firstElement();
			int ind = choice.getSelectedIndex();
			if (alreadyChoosen[ind]) {
				GenericDialog gd2 = new GenericDialog("Warning");
				gd2.addMessage("Voulez vous �craser la position pr�c�dente du bouton '" + choice.getSelectedItem() + "'?");
				gd2.showDialog();
				
				if (gd2.wasOKed()) {
					int ind2 = overlay.getIndex(choice.getSelectedItem());
					overlay.remove(ind2);
					imp.updateAndDraw();
					coordinates[ind][0] = x;
					coordinates[ind][1] = y;
				}
				if (gd2.wasCanceled()) {
					int ind2 = overlay.getIndex("tmp");
					overlay.remove(ind2);
					imp.updateAndDraw();
				}
				
			} else {
				alreadyChoosen[ind] = true;
				point.setName(choice.getSelectedItem());
				coordinates[ind][0] = x;
				coordinates[ind][1] = y;
			}
		}
		if (gd.wasCanceled()) {
			int ind2 = overlay.getIndex("tmp");
			overlay.remove(ind2);
			imp.updateAndDraw();
		}
		if (alreadyChoosen[0] && alreadyChoosen[1] && alreadyChoosen[2] && alreadyChoosen[3] && alreadyChoosen[4]) {
			YesNoCancelDialog d = new YesNoCancelDialog(null, "", "Souhaitez-vous passer � l'�tape suivante?");
			if (d.yesPressed()) next = true;
			if (d.cancelPressed()) canceled = true;
		}
	}

	public boolean next() {
		return next;
	}
	
	public boolean canceled() {
		return canceled;
	}

	public int[][] getCoordinates() {
		return coordinates;
	}
}
