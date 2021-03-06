/*******************************************************************************
 * Copyright (c) offset11, offset12 Red Hat, Inc.
 *  All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-voffset.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 *
 * @author Bob Brodt
 ******************************************************************************/
package org.eclipse.bpmn2.modeler.core.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.bpmn2.modeler.core.utils.AnchorSite;
import org.eclipse.bpmn2.modeler.core.utils.AnchorType;
import org.eclipse.bpmn2.modeler.core.utils.AnchorUtil;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil;
import org.eclipse.bpmn2.modeler.core.utils.GraphicsUtil.LineSegment;
import org.eclipse.graphiti.datatypes.IDimension;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.algorithms.styles.Point;
import org.eclipse.graphiti.mm.pictograms.Anchor;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.ContainerShape;
import org.eclipse.graphiti.mm.pictograms.FreeFormConnection;

/**
 * A Connection Router that constrains all line segments of a connection to be either
 * horizontal or vertical; thus, diagonal lines are split into two segments that are
 * horizontal and vertical.
 * 
 * This is a final class because it needs to ensure the routing info for
 * the connection is cleaned up when it's done, so we don't want to allow
 * this class to be subclassed.
 */
public class ManhattanConnectionRouter extends BendpointConnectionRouter {
	
	/** The Constant offset. */
	static final int margin = 20;
	
	/** The test route solver. */
	static boolean testRouteSolver = false;
	
	/**
	 * The connection routing directions.
	 */
	public enum Direction {
		NONE, UP, DOWN, LEFT, RIGHT;

		/**
		 * Determine the direction of the vector defined by the points [start,end].
		 * If the vector is diagonal, return the direction of the longest horizontal
		 * or vertical component.
		 *  
		 * @param start
		 * @param end
		 * @return
		 */
		public static Direction get(Point start, Point end) {
			int dx = start.getX() - end.getX();
			int dy = start.getY() - end.getY();
			if (dy == 0) {
				if (dx > 0)
					return LEFT;
				if (dx < 0)
					return RIGHT;
			} else if (dx == 0) {
				if (dy > 0)
					return UP;
				if (dy < 0)
					return DOWN;
			} else if (dy > 0) {
				if (dx > 0) {
					if (Math.abs(dx)>Math.abs(dy))
						return LEFT;
					return UP;
				}
				if (dx < 0) {
					if (Math.abs(dx)>Math.abs(dy))
						return RIGHT;
					return UP;
				}
			} else if (dy < 0) {
				if (dx > 0) {
					if (Math.abs(dx)>Math.abs(dy))
						return LEFT;
					return DOWN;
					
				}
				if (dx < 0) {
					if (Math.abs(dx)>Math.abs(dy))
						return RIGHT;
					return DOWN;
				}
			}
			return Direction.NONE;
		}

		/**
		 * Calculate a new direction for the vector defined by the points
		 * [start,end]. If the vector is diagonal, return the old direction
		 * value if possible. That is, if the old direction is the same as one
		 * of the horizontal or vertical components of the vector.
		 * 
		 * @param oldDirection an arbitrary Direction
		 * @param start origin of the vector
		 * @param end endpoint of the vector
		 * @return a new Direction
		 */
		public static Direction get(Direction oldDirection, Point start, Point end) {
			int dx = start.getX() - end.getX();
			int dy = start.getY() - end.getY();
			if (dy == 0) {
				if (dx > 0)
					return LEFT;
				if (dx < 0)
					return RIGHT;
			} else if (dx == 0) {
				if (dy > 0)
					return UP;
				if (dy < 0)
					return DOWN;
			} else if (dy > 0) {
				if (dx > 0) {
					if (oldDirection==LEFT || oldDirection==UP)
						return oldDirection;
					if (Math.abs(dx)>Math.abs(dy))
						return LEFT;
					return UP;
				}
				if (dx < 0) {
					if (oldDirection==RIGHT || oldDirection==UP)
						return oldDirection;
					if (Math.abs(dx)>Math.abs(dy))
						return RIGHT;
					return UP;
				}
			} else if (dy < 0) {
				if (dx > 0) {
					if (oldDirection==LEFT || oldDirection==DOWN)
						return oldDirection;
					if (Math.abs(dx)>Math.abs(dy))
						return LEFT;
					return DOWN;
					
				}
				if (dx < 0) {
					if (oldDirection==RIGHT || oldDirection==DOWN)
						return oldDirection;
					if (Math.abs(dx)>Math.abs(dy))
						return RIGHT;
					return DOWN;
				}
			}
			return Direction.NONE;
		}

		/**
		 * Translate an AnchorSite to a Direction.
		 * 
		 * @param site the AnchorSite
		 * @return the Direction that corresponds to the AnchorSite
		 */
		public static Direction get(AnchorSite site) {
			switch (site) {
			case TOP:
				return UP;
			case BOTTOM:
				return DOWN;
			case LEFT:
				return LEFT;
			case RIGHT:
				return RIGHT;
			}
			return NONE;
		}

		/**
		 * Reverse the given direction by 180 degrees.
		 * 
		 * @param direction the original direction
		 * @return the original direction transposed 180 degrees
		 */
		public static Direction reverse(Direction direction) {
			switch (direction) {
			case DOWN:
				return UP;
			case UP:
				return DOWN;
			case RIGHT:
				return LEFT;
			case LEFT:
				return RIGHT;
			}
			return NONE;
		}
	}
	
	/**
	 * Instantiates a new manhattan connection router.
	 *
	 * @param fp the fp
	 */
	public ManhattanConnectionRouter(IFeatureProvider fp) {
		super(fp);
	}
	
	@Override
	public boolean routingNeeded(Connection connection) {
		if (connection instanceof FreeFormConnection) {
			if (peService.getPropertyValue(connection, GraphitiConstants.INITIAL_UPDATE) != null)
				return true;
			if (forceRouting(connection))
				return true;

			initialize(connection);

			int length = oldPoints.length;
			int i = 0;
			Point p1 = oldPoints[i++];
			Point p2;
			while (i<length) {
				p2 = oldPoints[i++];
				if (!isHorizontal(p1, p2) && !isVertical(p1, p2))
					return true;
				if (getCollision(p1,p2)!=null)
					return true;
				if (GraphicsUtil.getLength(p1, p2) < margin)
					return true;
				p1 = p2;
			}
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.bpmn2.modeler.core.features.BendpointConnectionRouter#calculateRoute()
	 */
	@Override
	protected ConnectionRoute calculateRoute() {
		
		if (isSelfConnection())
			return super.calculateRoute();
		
		GraphicsUtil.debug = false;
		
		boolean initialUpdate = (peService.getPropertyValue(ffc, GraphitiConstants.INITIAL_UPDATE) != null);
		if (initialUpdate) {
			peService.removeProperty(ffc, GraphitiConstants.INITIAL_UPDATE);
		}
		
		Point start = null;
		Point end = null;

		if (testRouteSolver) {
			RouteSolver solver = new RouteSolver(fp, allShapes);
			boolean b = solver.solve(source, target);
			if (b) return null;
		}
		
		// The list of all possible routes. The shortest will be used.
		List<ConnectionRoute> allRoutes = new ArrayList<ConnectionRoute>();
		AnchorSite sourceSite = AnchorSite.getSite(sourceAnchor);
		AnchorSite targetSite = AnchorSite.getSite(targetAnchor);
		AnchorSite initialSourceSite = sourceSite;
		AnchorSite initialTargetSite = targetSite;
		Point initialSourceLocation = createPoint(sourceAnchor);
		Point initialTargetLocation = createPoint(targetAnchor);
		int length = oldPoints.length;

		if (movedBendpoint==null && length>2) {
			// Can we fix the existing route (assuming some segments are already orthogonal)?
			IDimension size = GraphicsUtil.calculateSize(source);
			int width = size.getWidth() + margin;
			int height = size.getHeight() + margin;
			int d;
			int dx = 0;
			int dy = 0;
			boolean canFix = true;
			
			Point p = oldPoints[1];
			if (sourceSite==AnchorSite.LEFT) {
				d = initialSourceLocation.getX() - p.getX();
				if (d < margin) {
					if (d + width < margin)
						canFix = false;
					else
						dx = d - margin;
				}
			}
			else if (sourceSite==AnchorSite.RIGHT) {
				d = p.getX() - initialSourceLocation.getX();
				if (d < margin) {
					if (d + width < margin)
						canFix = false;
					else
						dx = margin - d;
				}
			}
			else if (sourceSite==AnchorSite.TOP) {
				d = initialSourceLocation.getY() - p.getY();
				if (d < margin) {
					if (d + height < margin)
						canFix = false;
					else
						dy = d - margin;
				}
			}
			else if (sourceSite==AnchorSite.BOTTOM) {
				d = p.getY() - initialSourceLocation.getY();
				if (d < margin) {
					if (d + height < margin)
						canFix = false;
					else
						dy = margin - d;
				}
			}

			p = oldPoints[length-2];
			size = GraphicsUtil.calculateSize(target);
			width = size.getWidth() + margin;
			height = size.getHeight() + margin;

			if (targetSite==AnchorSite.LEFT) {
				d = initialTargetLocation.getX() - p.getX();
				if (d < margin) {
					if (d + width < margin || dx!=0)
						canFix = false;
					else
						dx = d - margin;
				}
			}
			else if (targetSite==AnchorSite.RIGHT) {
				d = p.getX() - initialTargetLocation.getX();
				if (d < margin) {
					if (d + width < margin || dx!=0)
						canFix = false;
					else
						dx = margin - d;
				}
			}
			else if (targetSite==AnchorSite.TOP) {
				d = initialTargetLocation.getY() - p.getY();
				if (d < margin) {
					if (d + height < margin || dy!=0)
						canFix = false;
					else
						dy = d - margin;
				}
			}
			else if (targetSite==AnchorSite.BOTTOM) {
				d = p.getY() - initialTargetLocation.getY();
				if (d < margin) {
					if (d + height < margin || dy!=0)
						canFix = false;
					else
						dy = margin - d;
				}
			}

			if (canFix) {
				Point p1;
				Point p2;
				Point p3;
				if (dx!=0 || dy!=0) {
					for (int i=1; i<length-1; ++i) {
						p1 = oldPoints[i];
						p1.setX( p1.getX() + dx );
						p1.setY( p1.getY() + dy );
					}
				}
				
				for (int loop=0; loop<2; ++loop) {
					ConnectionRoute route = new ConnectionRoute(this, 0, source,target);
					route.setSourceAnchor(sourceAnchor);
					route.setTargetAnchor(targetAnchor);
					
					int i = 0;
					p1 = oldPoints[i++];
					route.add(p1);
					while (true) {
						p2 = oldPoints[i++];
						if (i==length)
							break;
						if (!isHorizontal(p1, p2) && !isVertical(p1, p2)) {
							p3 = oldPoints[i];
							if (isHorizontal(p2, p3)) {
								p2.setX(p1.getX());
							}
							else {
								p2.setY(p1.getY());
							}
						}
						route.add(p2);
						ContainerShape shape = getCollision(p1,p2);
						if (shape!=null)
							route.addCollision(shape, p1,p2);
						p1 = p2;
					}
					// check the last segment just before the target shape
					if (!isHorizontal(p1, p2) && !isVertical(p1, p2)) {
						// since we can't change the target point (it is connected
						// to an anchor on the shape) we have to change the
						p3 = oldPoints[length-2];
						if (isHorizontal(p3, p1)) {
							p1.setX(p2.getX());
						}
						else {
							p1.setY(p2.getY());
						}
					}
					else {
						route.addCollision(getCollision(p1,p2), p1,p2);
						if (route.getCollisions().size()==0) {
							route.add(p2);
							allRoutes.add(route);
							break;
						}
					}
				}
			}
		}
		
		// Calculate all possible routes: this iterates over every permutation
		// of 4 sides for both source and target shape
		for (int i=0; i<16; ++i) {
			int rank = 1;
			if (!shouldCalculate(sourceSite, targetSite)) {
				++rank;
			}
			AnchorUtil.moveAnchor(sourceAnchor, initialSourceLocation);
			AnchorUtil.moveAnchor(targetAnchor, initialTargetLocation);
			AnchorSite.setSite(sourceAnchor, sourceSite);
			AnchorUtil.adjustAnchors(source);
			AnchorSite.setSite(targetAnchor, targetSite);
			AnchorUtil.adjustAnchors(target);
			
			ConnectionRoute route = new ConnectionRoute(this, allRoutes.size()+1, source,target);

			// Get the starting and ending points on the (possibly relocated)
			// source and target anchors.
			start = createPoint(sourceAnchor);
			end = createPoint(targetAnchor);
			
			// If either the source or target anchor is a "Pool" anchor
			// (i.e. attached to a Pool) then try to move it so it lines
			// up either vertically or horizontally with the other anchor.
			// This is only done for these conditions:
			// 1. this is an initial update, i.e. the Connection has just been created
			// 2. the Connection was manually moved
			// 3. the edge to which the Connection was attached has changed
			if (initialUpdate || movedBendpoint!=null ||
					sourceSite!=initialSourceSite || targetSite!=initialTargetSite) {
				if (AnchorType.getType(targetAnchor) == AnchorType.POOL) {
					if (movedBendpoint!=null)
						AnchorUtil.moveAnchor(targetAnchor, movedBendpoint);
					else
						AnchorUtil.moveAnchor(targetAnchor, sourceAnchor);
					end = createPoint(targetAnchor);
					if (targetSite!=initialTargetSite)
						++rank;
				}
				if (AnchorType.getType(sourceAnchor) == AnchorType.POOL) {
					if (movedBendpoint!=null)
						AnchorUtil.moveAnchor(sourceAnchor, movedBendpoint);
					else
						AnchorUtil.moveAnchor(sourceAnchor, targetAnchor);
					start = createPoint(sourceAnchor);
					if (sourceSite!=initialSourceSite)
						++rank;
				}
			}
			if ((sourceSite!=initialSourceSite || targetSite!=initialTargetSite))
				++rank;
			route.setRank(rank);
			route.setSourceAnchor(sourceAnchor);
			route.setTargetAnchor(targetAnchor);
			
			calculateRoute(route, sourceSite, start, targetSite, end);

			allRoutes.add(route);

			if ((i % 4)==0) {
				sourceSite = getNextAnchorSite(sourceSite);
			}
			else {
				targetSite = getNextAnchorSite(targetSite);
			}
		}
		
		// pick the "best" route
		ConnectionRoute route = null;
		if (allRoutes.size()==1) {
			route = allRoutes.get(0);
			optimize(route);
			GraphicsUtil.dump("Only one valid route: "+route.toString()); //$NON-NLS-1$
		}
		else if (allRoutes.size()>1) {
			GraphicsUtil.dump("Optimizing Routes:\n------------------"); //$NON-NLS-1$
			for (ConnectionRoute r : allRoutes) {
				optimize(r);
			}

			GraphicsUtil.dump("Calculating Crossings:\n------------------"); //$NON-NLS-1$
			// Connection crossings only participate in determining the best route,
			// we don't actually try to correct a route crossing a connection.
			for (ConnectionRoute r : allRoutes) {
				if (r.getPoints().size()>1) {
					Point p1 = r.get(0);
					for (int i=1; i<r.getPoints().size(); ++i) {
						Point p2 = r.get(i);
						List<Connection> crossings = findCrossings(connection, p1, p2);
						for (Connection c : crossings) {
							if (c!=this.connection)
								r.addCrossing(c, p1, p2);
						}
						ContainerShape shape = getCollision(p1, p2);
						if (shape!=null) {
							r.addCollision(shape, p1, p2);
						}
						
						p1 = p2;
					}

				}
				GraphicsUtil.dump("    "+r.toString()); //$NON-NLS-1$
			}

			GraphicsUtil.dump("Sorting Routes:\n------------------"); //$NON-NLS-1$
			Collections.sort(allRoutes);
			
			drawConnectionRoutes(allRoutes);

			route = allRoutes.get(0);
		}
		
		return route;
	}
	
	/**
	 * Calculate route.
	 *
	 * @param allRoutes the all routes
	 * @param source the source
	 * @param start the start
	 * @param target the target
	 * @param end the end
	 * @return the connection route
	 */
	ConnectionRoute calculateRoute(ConnectionRoute route, AnchorSite sourceSite, Point start, AnchorSite targetSite, Point end) {
		
		if (movedBendpoint!=null) {
			List<Point> departure = calculateDeparture(sourceSite, start, movedBendpoint);
			List<Point> approach = calculateApproach(targetSite, movedBendpoint, end);

			route.getPoints().addAll(departure);
			if (calculateEnroute(route, departure.get(departure.size()-1), movedBendpoint)) {
				route.add(movedBendpoint);
				if (calculateEnroute(route, movedBendpoint,approach.get(0))) {
					route.getPoints().addAll(approach);
				}
				else
					route.add(end);
			}
			else
				route.add(end);
		}
		else {
			List<Point> departure = calculateDeparture(sourceSite, start, end);
			List<Point> approach = calculateApproach(targetSite, start, end);
			route.getPoints().addAll(departure);
			start = departure.get(departure.size()-1);
			end = approach.get(0);
			calculateEnroute(route, start, end);
			route.getPoints().addAll(approach);
		}
		
		return route;
	}

	/**
	 * Calculate departure.
	 *
	 * @param source the source
	 * @param start the start
	 * @param end the end
	 * @return the list
	 */
	List<Point> calculateDeparture(AnchorSite sourceSite, Point start, Point end) {
		List<Point> points = new ArrayList<Point>();
		
		Point p = createPoint(start);
		Point m = createPoint(end);
		ContainerShape shape;
		
		switch (sourceSite) {
		case TOP:
		case BOTTOM:
			m.setX(start.getX());
			for (;;) {
				m = getVertMidpoint(start,m,0.5);
				adjustPoint(sourceSite, m, start);
				shape = getCollision(start,m);
				if (shape==null || Math.abs(m.getY()-start.getY())<=margin) {
					if (shape!=null) {
						// still collision?
						if (sourceSite==AnchorSite.BOTTOM)
							m.setY(start.getY() + margin);
						else
							m.setY(start.getY() - margin);
					}
					break;
				}
			}
			p.setY( m.getY() );
			break;
		case LEFT:
		case RIGHT:
			m.setY(start.getY());
			for (;;) {
				m = getHorzMidpoint(start,m,0.5);
				adjustPoint(sourceSite, m, start);
				shape = getCollision(start,m);
				if (shape==null || Math.abs(m.getX()-start.getX())<=margin) {
					if (shape!=null) {
						// still collision?
						if (sourceSite==AnchorSite.RIGHT)
							m.setX(start.getX() + margin);
						else
							m.setX(start.getX() - margin);
					}
					break;
				}
			}
			p.setX( m.getX() );
			break;
		default:
			return points;
		}
		
		points.add(start);
		points.add(p);
		
		return points;
	}
	
	/**
	 * Calculate approach.
	 * @param start the start
	 * @param end the end
	 * @param target the target
	 *
	 * @return the list
	 */
	List<Point> calculateApproach(AnchorSite targetSite, Point start, Point end) {
		List<Point> points = new ArrayList<Point>();
		
		Point p = createPoint(end);
		Point m = createPoint(start);

		switch (targetSite) {
		case TOP:
		case BOTTOM:
			m.setX(end.getX());
			for (;;) {
				m = getVertMidpoint(m,end,0.5);
				adjustPoint(m, end, targetSite);
				ContainerShape shape = getCollision(m,end);
				if (shape==null || Math.abs(m.getY()-end.getY())<=margin) {
					if (shape!=null) {
						// still collision?
						if (targetSite==AnchorSite.BOTTOM)
							m.setY(end.getY() + margin);
						else
							m.setY(end.getY() - margin);
					}
					break;
				}
			}
			p.setY( m.getY() );
			break;
		case LEFT:
		case RIGHT:
			m.setY(end.getY());
			for (;;) {
				m = getHorzMidpoint(m,end,0.5);
				adjustPoint(m, end, targetSite);
				ContainerShape shape = getCollision(m,end);
				if (shape==null || Math.abs(m.getX()-end.getX())<=margin) {
					if (shape!=null) {
						// still collision?
						if (targetSite==AnchorSite.RIGHT)
							m.setX(end.getX() + margin);
						else
							m.setX(end.getX() - margin);
					}
					break;
				}
			}
			p.setX( m.getX() );
			break;
		default:
			points.add(p);
			return points;
		}
		
		points.add(p);
		points.add(end);
		
		return points;
	}
	
	/**
	 * Calculate enroute.
	 *
	 * @param route the route
	 * @param start the start
	 * @param end the end
	 * @return true, if successful
	 */
	boolean calculateEnroute(ConnectionRoute route, Point start, Point end) {
		if (GraphicsUtil.pointsEqual(start, end))
			return false;
		
		// special case: if start and end can be connected with a horizontal or vertical line
		// check if there's a collision in the way. If so, we need to navigate around it.
		if (!GraphicsUtil.isSlanted(start,end) && getCollision(start,end)==null) {
			return true;
		}
		
		Point nextPoint = createPoint(end);
		Point p0 = route.get(route.size()-2);
		Point p1 = route.get(route.size()-1);
		Direction oldDirection = Direction.get(p0,p1);
		Direction newDirection = Direction.get(oldDirection, start,end);
		
		switch (newDirection) {
		case UP:
			nextPoint = createPoint(start.getX(), end.getY());
			if (!calculateDetour(route, newDirection, start, nextPoint, end))
				route.add(nextPoint);
			break;
		case DOWN:
			nextPoint = createPoint(start.getX(), end.getY());
			if (!calculateDetour(route, newDirection, start, nextPoint, end))
				route.add(nextPoint);
			break;
		case LEFT:
			nextPoint = createPoint(end.getX(), start.getY());
			if (!calculateDetour(route, newDirection, start, nextPoint, end))
				route.add(nextPoint);
			break;
		case RIGHT:
			nextPoint = createPoint(end.getX(), start.getY());
			if (!calculateDetour(route, newDirection, start, nextPoint, end))
				route.add(nextPoint);
			break;
		}
		
		if (route.isValid()){
			nextPoint = route.get(route.size()-1);
			if (!calculateEnroute(route,nextPoint,end))
				return false;
		}
		
		return route.isValid();
	}
	
	boolean calculateDetour(ConnectionRoute route, Direction direction, Point start, Point nextPoint, Point end) {
		
		ContainerShape shape = getCollision(start,nextPoint);
		if (shape!=null) {
			int d0, d1;
			DetourPoints detour = getDetourPoints(shape);
			
			switch (direction) {
			case UP:
				// approach from bottom of shape: go left or right?
				d0 = Math.abs(nextPoint.getX()-detour.bottomLeft.getX());
				d1 = Math.abs(nextPoint.getX()-detour.bottomRight.getX());
				if (d0 < d1) {
					// go left
					nextPoint.setY( detour.bottomLeft.getY() );
					route.add(nextPoint);
					route.add(detour.bottomLeft);
//					route.add(detour.topLeft);
				}
				else {
					// go right
					nextPoint.setY( detour.bottomRight.getY() );
					route.add(nextPoint);
					route.add(detour.bottomRight);
//					route.add(detour.topRight);
				}
				break;
			case DOWN:
				// approach from top of shape: go left or right?
				d0 = Math.abs(nextPoint.getX()-detour.topLeft.getX());
				d1 = Math.abs(nextPoint.getX()-detour.topRight.getX());
				if (d0 < d1) {
					// go left
					nextPoint.setY( detour.topLeft.getY() );
					route.add(nextPoint);
					route.add(detour.topLeft);
//					route.add(detour.bottomLeft);
				}
				else {
					// go right
					nextPoint.setY( detour.topRight.getY() );
					route.add(nextPoint);
					route.add(detour.topRight);
//					route.add(detour.bottomRight);
				}
				break;
			case LEFT:
				// approach from right of shape: go up or down?
				d0 = Math.abs(nextPoint.getY()-detour.topRight.getY());
				d1 = Math.abs(nextPoint.getY()-detour.bottomRight.getY());
				if (d0 < d1) {
					// go up
					nextPoint.setX( detour.topRight.getX() );
					route.add(nextPoint);
					route.add(detour.topRight);
//					route.add(detour.topLeft);
				}
				else {
					// go down
					nextPoint.setX( detour.bottomRight.getX() );
					route.add(nextPoint);
					route.add(detour.bottomRight);
//					route.add(detour.bottomLeft);
				}
				break;
			case RIGHT:
				// approach from left of shape: go up or down?
				d0 = Math.abs(nextPoint.getY()-detour.topLeft.getY());
				d1 = Math.abs(nextPoint.getY()-detour.bottomLeft.getY());
				if (d0 < d1) {
					// go up
					nextPoint.setX( detour.topLeft.getX() );
					route.add(nextPoint);
					route.add(detour.topLeft);
//					route.add(detour.topRight);
				}
				else {
					// go down
					nextPoint.setX( detour.bottomLeft.getX() );
					route.add(nextPoint);
					route.add(detour.bottomLeft);
//					route.add(detour.bottomRight);
				}
				break;
			default:
				return false;
			}
		}
		return shape!=null;
	}
	
	Point getVertMidpoint(Point start, Point end, double fract) {
		Point m = createPoint(start);
		int d = (int)(fract * (double)(end.getY() - start.getY()));
		m.setY(start.getY()+d);
		return m;
	}
	
	Point getHorzMidpoint(Point start, Point end, double fract) {
		Point m = createPoint(start);
		int d = (int)(fract * (double)(end.getX() - start.getX()));
		m.setX(start.getX()+d);
		return m;
	}

	/**
	 * Convenience method to create a point given x,y coordinates.
	 *
	 * @param x the x
	 * @param y the y
	 * @return the point
	 */
	Point createPoint(int x, int y) {
		return GraphicsUtil.createPoint(x, y); 
	}
	
	Point createPoint(Point p) {
		return GraphicsUtil.createPoint(p); 
	}
	
	Point createPoint(Anchor a) {
		return GraphicsUtil.createPoint(a); 
	}
	
	boolean isHorizontal(Point p1, Point p2) {
		return GraphicsUtil.isHorizontal(p1, p2);
	}
	
	boolean isVertical(Point p1, Point p2) {
		return GraphicsUtil.isVertical(p1, p2);
	}
	
	DetourPoints getDetourPoints(ContainerShape shape) {
		DetourPoints detour = new DetourPoints(shape, margin);
		if (allShapes==null)
			findAllShapes();

		for (int i=0; i<allShapes.size(); ++i) {
			ContainerShape s = allShapes.get(i);
			if (shape==s || shape==source || shape==target)
				continue;
			DetourPoints d = new DetourPoints(s, margin);
			if (detour.intersects(d) && !detour.contains(d)) {
				detour.merge(d);
				i = -1;
			}
		}

		return detour;
	}

	boolean adjustPoint(AnchorSite site, Point p1, Point p2) {
		return adjustPoint(Direction.get(site), p1, p2);
	}

	boolean adjustPoint(Point p1, Point p2, AnchorSite site) {
		return adjustPoint(Direction.reverse(Direction.get(site)), p1, p2);
	}
	
	boolean adjustPoint(Direction direction, Point p1, Point p2) {

		switch (Direction.get(p1,p2)) {
		case UP:
			if (p1.getY() - p2.getY() < margin) {
				p1.setY( p2.getY() - margin );
				return true;
			}
			break;
		case DOWN:
			if (p2.getY() - p1.getY() < margin) {
				p1.setY( p2.getY() - margin );
				return true;
			}
			break;
		case LEFT:
			if (p1.getX() - p2.getX() < margin) {
				p1.setX( p2.getX() - margin );
				return true;
			}
			break;
		case RIGHT:
			if (p2.getX() - p1.getX() < margin) {
				p1.setX( p2.getX() + margin );
				return true;
			}
			break;
		}
		return false;
	}
	
	void optimize(ConnectionRoute route) {

		route.addSpecial(movedBendpoint);
		
		Point p1;
		Point p2;
		
		/*
		 * Adjust differences of 1 pixel between two consecutive points
		 * to account for round-off errors.
		 */
		p1 = route.get(0);
		for (int i = 1; i < route.size(); ++i) {
			p2 = route.get(i);
			if (Math.abs(p1.getX() - p2.getX()) <= 1)
				p2.setX(p1.getX());
			if (Math.abs(p1.getY() - p2.getY()) <= 1)
				p2.setY(p1.getY());
			if (GraphicsUtil.isSlanted(p1, p2))
				route.setRank(5);
			p1 = p2;
		}

		// remove unnecessary line segments
		route.optimize();
		
		int size = route.size();
		if (size>1) {
			// Discourage routes containing starting or ending segments that touch
			// the source or target shape - it just looks ugly.
			LineSegment sourceEdges[] = GraphicsUtil.getEdges(source);
			p1 = route.get(0);
			p2 = route.get(1);
			AnchorSite sourceSite = route.getSourceAnchorSite();
			if (sourceSite==AnchorSite.LEFT || sourceSite==AnchorSite.RIGHT) {
				int x = sourceEdges[sourceSite.ordinal()].getStart().getX();
				if (isVertical(p1, p2) && p1.getX()==x)
					route.setRank(4);
			}
			else if (sourceSite==AnchorSite.TOP || sourceSite==AnchorSite.BOTTOM) {
				int y = sourceEdges[sourceSite.ordinal()].getStart().getY();
				if (isHorizontal(p1, p2) && p1.getY()==y)
					route.setRank(4);
			}
			if (size>2) {
				if (GraphicsUtil.getLength(p1, p2) < margin) {
					p1 = route.get(1);
					p2 = route.get(2);
					if (sourceSite==AnchorSite.LEFT || sourceSite==AnchorSite.RIGHT) {
						if (isVertical(p1, p2))
							route.setRank(4);
					}
					else if (sourceSite==AnchorSite.TOP || sourceSite==AnchorSite.BOTTOM) {
						if (isHorizontal(p1, p2))
							route.setRank(4);
					}
				}
				// Same as above, but for the target shape
				LineSegment targetEdges[] = GraphicsUtil.getEdges(target);
				p1 = route.get(size-2);
				p2 = route.get(size-1);
				AnchorSite targetSite = route.getTargetAnchorSite();
				if (targetSite==AnchorSite.LEFT || targetSite==AnchorSite.RIGHT) {
					int x = targetEdges[targetSite.ordinal()].getStart().getX();
					if (isVertical(p1, p2) && p1.getX()==x)
						route.setRank(4);
				}
				else if (targetSite==AnchorSite.TOP || targetSite==AnchorSite.BOTTOM) {
					int y = targetEdges[targetSite.ordinal()].getStart().getY();
					if (isHorizontal(p1, p2) && p1.getY()==y)
						route.setRank(4);
				}
				if (GraphicsUtil.getLength(p1, p2) < margin) {
					p1 = route.get(size-3);
					p2 = route.get(size-2);
					if (targetSite==AnchorSite.LEFT || targetSite==AnchorSite.RIGHT) {
						if (isVertical(p1, p2))
							route.setRank(4);
					}
					else if (targetSite==AnchorSite.TOP || targetSite==AnchorSite.BOTTOM) {
						if (isHorizontal(p1, p2))
							route.setRank(4);
					}
				}
			}
		}
	}
}
