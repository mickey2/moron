/*
 * (C) Copyright 2005 Davide Brugali, Marco Torchiano
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307  USA
 */

// package moro;

/**
 * Title:        The MObile RObot Simulation Environment
 * Description:  The laser accepts commands from the controller and executes them in steps.
 *               The most important tasks are rotating the laser and getting measurements
 *               from the /0.
 * Copyright:    Copyright (c) 2001
 * Company:      Universit di Bergamo
 * @author Davide Brugali
 * @version 1.0
 */

import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

public class Laser extends Device{
	// JB: The fact that this code has a lot of comments with question marks in it (see below)
	// does not give a lot of confidence. I have chosen to let the comments in, as they could be
	// indicators where to look if things break down.
	
	int orientation = 1;      // 1: clockwise  -1: otherwise
	double rotStep = 1.0;     // one degree
	double numSteps = 0;
	
	// JB: The use of the booleans detect and scan (and Device.running) makes the code very complex
	// and easy to break. See executeCommand() and nextStep(). This could do with a decent refactoring!
	boolean detect = false;
	boolean scan = false;
	int range = 100;          // maximum range

	Measure detectMeasure = null;
	ArrayList<Measure> scanMeasures = new ArrayList<Measure>();

	public Laser(String name, Robot robot, Position localPos, Environment environment) {
		super(name, robot, localPos, environment);
		bgColor = Color.cyan;
		this.addPoint(0, 2);
		this.addPoint(100, 2);
		this.addPoint(100, -2);
		this.addPoint(0, -2);
	}

	public double read(boolean first) {
		Point2D centre = new Point2D.Double(localPos.getX(), localPos.getY());
		Point2D front = new Point2D.Double(localPos.getX() + range * Math.cos(localPos.getT()),
				localPos.getY() + range * Math.sin(localPos.getT()));
		// reads the robot's position
		robot.readPosition(robotPos);
		// center's coordinates according to the robot position
		robotPos.rototras(centre);
		// front's coordinates according to the robot position
		robotPos.rototras(front);

		double minDistance = -1.0;
		for(int i=0; i < environment.obstacles.size(); i++) {
			// This is really dirty: the laser uses direct access to environment's obstacles
			Obstacle obstacle = (Obstacle) environment.obstacles.get(i);
			if(obstacle.opaque)
				continue;
			double dist = pointToObstacle(obstacle.polygon, centre, front, first);
			if(minDistance == -1.0 || (dist > 0 && dist < minDistance)) {
				minDistance = dist;
				if(minDistance > -1 && first) {
					return minDistance;
				}
			}
		}
		if(minDistance > 0)
			return minDistance;
		return -1.0;
	}

	// receives the vertex coordinates of segment beam;
	// if segment beam intersects an edge of this PhysicalShape, it returns
	// the distance of the first vertex of beam from the closest edge
	// if beam does not intersect the PhysicalShape, the return value is -1.0
	public double pointToObstacle(Polygon polygon, Point2D centre, Point2D front, boolean first) {
		int j = 0;
		double minDistance = -1.0;
		double dist = -1.0;
		double px, py;
		double x1, y1, x2, y2;
		double m1, q1, m2, q2;
		Line2D.Double beam = new Line2D.Double(centre, front);

		for(int i=0; i < polygon.npoints; i++) {
			j = i+1;
			if(j == polygon.npoints)
				j = 0;
			x1 = polygon.xpoints[i];
			y1 = polygon.ypoints[i];
			x2 = polygon.xpoints[j];
			y2 = polygon.ypoints[j];
			if(beam.intersectsLine(x1, y1, x2, y2)) {
				// calculates the intersection point
				if(centre.getX() == front.getX()) {
					px = centre.getX();
					py = (y2 - y1) / (x2 - x1) * (px - x1) + y1;
				}
				else
					if(x1 == x2) {
						px = x1;
						py = (front.getY()-centre.getY()) / (front.getX()-centre.getX()) * (px - centre.getX()) + centre.getY();
					}
					else {
						m1 = (y2 - y1) / (x2 - x1);
						q1 = y1 - m1 * x1;
						m2 = (front.getY()-centre.getY()) / (front.getX()-centre.getX());
						q2 = centre.getY() - m2 * centre.getX();
						px = (q2 - q1) / (m1 - m2);
						py = m1 * px + q1;
					}
				// calculates the distance between (cx, cy) and the intersection point
				dist = Point2D.Double.distance(centre.getX(), centre.getY(), px, py);
				if(minDistance == -1.0 || minDistance > dist)
					minDistance = dist;
				if(first && minDistance > 0.0)
					return minDistance;
			}
		}
		return minDistance;
	}

	public void executeCommand(String command) {
		if(command.indexOf("ROTATETO") > -1) {
			rotStep = 4.0;
			double direction = Math.abs(Double.parseDouble(command.trim().substring(9).trim()));
			while(direction < 0.0)
				direction+=360.0;
			while(direction > 360.0)
				direction-=360.0;
			double dirDiff = direction - Math.toDegrees(localPos.getT());   // ??????????????
			if(dirDiff >= 0.0 && dirDiff <= 180.0) {
				numSteps = dirDiff / rotStep;
				orientation = 1;
			}
			else if(dirDiff >= 0.0 && dirDiff > 180.0) {
				numSteps = (360.0 - dirDiff) / rotStep;
				orientation = -1;
			}
			else if(dirDiff < 0.0 && -dirDiff <= 180.0) {
				numSteps = -dirDiff / rotStep;
				orientation = -1;
			}
			else if(dirDiff < 0.0 && -dirDiff > 180.0) {
				numSteps = (360.0 + dirDiff) / rotStep;
				orientation = 1;
			}
			running = true;
		}
		else if(command.equalsIgnoreCase("READ")) {
			writeOut("t=" + Double.toString(this.localPos.getT()) + " d=" + Double.toString(this.read(true)));
			// ??????????????
		}
		else if(command.equalsIgnoreCase("SCAN")) {
			rotStep = 1.0;
			scanMeasures.clear();
			numSteps = 360.0 / rotStep;
			orientation = 1;
			scan = true;
			// send the list of measures
			commands.add("GETMEASURES");
			running = true;
		}
		else if(command.equalsIgnoreCase("GETMEASURES")) {
			Measure measure = null;
			String measures = "SCAN";
			for(int i=0; i < scanMeasures.size(); i++) {
				measure = scanMeasures.get(i);
				measures += " d=" + measure.distance + " t=" + measure.direction;
			}
			writeOut(measures);
		}
		else if(command.equalsIgnoreCase("DETECT")) {
			detect = true;
			rotStep = 8.0;
			if(detectMeasure != null) {
				writeOut("LASER DETECT d=" + detectMeasure.distance + " t=" + detectMeasure.direction);
				detectMeasure = null;
			}
			else if(localPos.getT() == Math.toRadians(45.0)) {   // ?????????????
				// move the laser to the left position
				commands.add("ROTATETO 315");
				// repeats this command
				commands.add("DETECT");
			}
			else if(localPos.getT() == Math.toRadians(315.0)) {  // ??????????????
				// move the laser to the right position
				commands.add("ROTATETO 45");
				// repeats this command
				commands.add("DETECT");
			}
			else {
				// move the laser to the right position
				commands.add("ROTATETO 45");
				// repeats this command
				commands.add("DETECT");
			}
		}
		else
			writeOut("DECLINED");
	}


	public void nextStep() {
		if(running && numSteps > 0.0) {
			if(numSteps < 1.0)
				localPos.rototras(0.0, 0.0, orientation*numSteps*rotStep);
			else
				localPos.rototras(0.0, 0.0, orientation*rotStep);
			environment.repaint();
			numSteps-=1.0;
			running = true;
		}
		else if(running) {
			running = false;
			if(!detect && !scan)
				writeOut("LASER ARRIVED");
		}
		if(detect) {
			double distance = this.read(true);
			if(distance > -1.0) {
				if(detectMeasure == null)
					detectMeasure = new Measure(distance, localPos.getT());  // ?????????????
				else if(detectMeasure.distance > distance)
					detectMeasure.set(distance, localPos.getT());  // ????????????
			}
		}
		else if(scan) {
			double distance = this.read(false);
			if(distance > -1.0)
				scanMeasures.add(new Measure(distance, localPos.getT()));  // ??????????????
		}
	}

	// Innerclass Measure represents measurements from the Laser
	class Measure {
		public double distance = 0;
		public double direction = 0.0;
		Measure(double dist, double dir) {
			distance = dist;
			direction = dir;
			while(direction >= 2.0*Math.PI)
				direction -= 2.0*Math.PI;
			while(direction < 0.0)
				direction += 2.0*Math.PI;
		}
		public void set(double dist, double dir) {
			distance = dist;
			direction = dir;
			while(direction >= 2.0*Math.PI)
				direction -= 2.0*Math.PI;
			while(direction < 0.0)
				direction += 2.0*Math.PI;
		}
	}
}
