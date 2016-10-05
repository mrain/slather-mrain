package slather.a1;

import slather.sim.Cell;
import slather.sim.Point;
import slather.sim.Move;
import slather.sim.Pherome;
import java.util.*;
import java.lang.*;

// Either there's a giant bug.
// Or I'm complete rekt by the random bot


public class Player implements slather.sim.Player {

	int size;
	double vision;
    private Random gen;

    public void init(double d, int t, int size) {
        gen = new Random();
		this.size = size;
		this.vision = d;
    }

    public Move play(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        if (player_cell.getDiameter() >= 2) // reproduce whenever possible
            return new Move(true, (byte)-1, (byte)-1);

		Set<Pherome> pheromes = new HashSet<Pherome>();
		for (Pherome pherome : nearby_pheromes)
			if (pherome.player != player_cell.player)
				pheromes.add(pherome);
		Set<Cell> cells = new HashSet<Cell>();
		for (Cell cell : nearby_cells)
			if (cell.player == player_cell.player)
				cells.add(cell);
		double angle = findTheLargestAngle(player_cell, nearby_cells, pheromes);
		Point next = new Point(Math.cos(angle), Math.sin(angle));

		double vision = this.vision;
		while (angle > Math.PI * 2 - 1 || collides(player_cell, next, nearby_cells, nearby_pheromes)) {
			vision *= 0.9;
			cells.clear();
			for (Cell cell : nearby_cells)
				if (cell.getPosition().distance(player_cell.getPosition()) <= vision)
					cells.add(cell);
			pheromes.clear();
			for (Pherome pherome : nearby_pheromes)
				if (pherome.getPosition().distance(player_cell.getPosition()) <= vision)
					pheromes.add(pherome);
			angle = findTheLargestAngle(player_cell, cells, pheromes);
			next = new Point(Math.cos(angle), Math.sin(angle));
			if (vision < 1 || angle < -Math.PI - 1) {
		//		angle = (double)memory * 2.0 * Math.PI / 128;
		//		next = new Point(Math.cos(angle), Math.sin(angle));
				break;
			}
		}
		if (Math.abs(angle) > Math.PI * 2 - 1 || collides(player_cell, next, nearby_cells, nearby_pheromes)) {
			angle = findTheLargestAngle(player_cell, cells, pheromes);
			next = new Point(Math.cos(angle), Math.sin(angle));
		}
		if (Math.abs(angle) > Math.PI * 2 - 1 || collides(player_cell, next, nearby_cells, nearby_pheromes)) {
			angle = (double)memory * 2.0 * Math.PI / 128;
			next = new Point(Math.cos(angle), Math.sin(angle));
		}
		if (Math.abs(angle) > Math.PI * 2 - 1 || collides(player_cell, next, nearby_cells, nearby_pheromes)) {
			angle = gen.nextDouble() * 2 * Math.PI - Math.PI;
			next = new Point(Math.cos(angle), Math.sin(angle));
		}
		int mem = (int) (angle / 2.0 / Math.PI * 128);
		return new Move(next, (byte) mem);
    }

        private class Event implements Comparable<Event> {
            public double value;
            public int index;

            public Event(double value, int index) {
                this.value = value;
                this.index = index;
            }

            public int compareTo(Event a) {
                return Double.compare(value, a.value);
            }
        };

	private double findTheLargestAngle(Cell cell, Set<Cell> cells, Set<Pherome> pheromes) {
		double result = Math.PI * 2;
		double radius = cell.getDiameter() * 0.5;

		List<Event> events = new ArrayList<Event>();

		for (Cell p : cells) {
			Point dir = correctedSubtract(p.getPosition(), cell.getPosition());
			if (dir.norm() < 1e-8) continue;
			double r = radius + p.getDiameter() * 0.5;
			double angle = Math.atan2(dir.y, dir.x);
			double delta = Math.asin(Math.min(1.0, r / dir.norm()));
			//System.out.println("(" + dir.x + "," + dir.y + ")" + delta);
			if (angle - delta < -Math.PI) {
				events.add(new Event(-Math.PI, 1));
				events.add(new Event(angle + delta, -1));
				events.add(new Event(angle - delta + 2.0 * Math.PI, 1));
				events.add(new Event(Math.PI, -1));
			} else if (angle + delta > Math.PI) {
				events.add(new Event(-Math.PI, 1));
				events.add(new Event(angle + delta - 2.0 * Math.PI, -1));
				events.add(new Event(angle - delta, 1));
				events.add(new Event(Math.PI, -1));
			} else {
				events.add(new Event(angle - delta, 1));
				events.add(new Event(angle + delta, -1));
			}
		}
		for (Pherome p : pheromes) {
			Point dir = correctedSubtract(p.getPosition(), cell.getPosition());
			if (dir.norm() < 1e-8) continue;
			double r = radius;
			double angle = Math.atan2(dir.y, dir.x);
			double delta = Math.asin(Math.min(1.0, r / dir.norm()));
			//System.out.println("(" + dir.x + "," + dir.y + ")" + r + "," + dir.norm() + "," + delta);
			if (angle - delta < -Math.PI) {
				events.add(new Event(-Math.PI, 1));
				events.add(new Event(angle + delta, -1));
				events.add(new Event(angle - delta + 2.0 * Math.PI, 1));
				events.add(new Event(Math.PI, -1));
			} else if (angle + delta > Math.PI) {
				events.add(new Event(-Math.PI, 1));
				events.add(new Event(angle + delta - 2.0 * Math.PI, -1));
				events.add(new Event(angle - delta, 1));
				events.add(new Event(Math.PI, -1));
			} else {
				events.add(new Event(angle - delta, 1));
				events.add(new Event(angle + delta, -1));
			}
		}

		Collections.sort(events);

		if (events.size() == 0) {
			//System.err.println("Dead game");
			return -result;
		}

		int stack = events.get(0).index;
		double width = -1;
		for (int i = 1; i < events.size(); ++ i) {
			if (stack == 0) {
				double w = events.get(i).value - events.get(i - 1).value;
				if (w > width) {
					width = w;
					result = events.get(i).value - w * 0.5;
				}
			}
			stack += events.get(i).index;
		}
		double w = events.get(0).value + 2 * Math.PI - events.get(events.size() - 1).value;
		if (w > width) {
			width = w;
			result = events.get(0).value - w * 0.5;
			if (result < -Math.PI) result += 2 * Math.PI;
		}
		if (width == -1) {
			// No angle available
		}

		System.out.print(events.size());
		for (Event event: events)
			System.out.print(" (" + event.value + "," + event.index + ")");
		System.out.println();
		System.out.println("width: " + width + ", angle: " + result);
		return result;
	}

    private Point add(Point a, Point b) {
        return new Point(a.x + b.x, a.y + b.y);
    }

    private Point subtract(Point a, Point b) {
        return new Point(a.x - b.x, a.y - b.y);
    }

    private Point correctedSubtract(Point a, Point b) {
        double x = a.x - b.x, y = a.y - b.y;
        if (Math.abs(x) > Math.abs(a.x + size - b.x)) x = a.x + size - b.x;
        if (Math.abs(x) > Math.abs(a.x - size - b.x)) x = a.x - size - b.x;
        if (Math.abs(y) > Math.abs(a.y + size - b.x)) y = a.y + size - b.y;
        if (Math.abs(y) > Math.abs(a.y - size - b.x)) y = a.y - size - b.y;

        return new Point(x, y);
    }

    private Point multiply(Point a, double d) {
        return new Point(a.x * d, a.y * d);
    }

    private Point normalize(Point a) {
        if (a.norm() < 1e-7) return a;
        else return multiply(a, 1.0/a.norm());
    }



    // check if moving player_cell by vector collides with any nearby cell or hostile pherome
    private boolean collides(Cell player_cell, Point vector, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        Iterator<Cell> cell_it = nearby_cells.iterator();
        Point destination = player_cell.getPosition().move(vector);
        while (cell_it.hasNext()) {
            Cell other = cell_it.next();
            if ( destination.distance(other.getPosition()) < 0.5*player_cell.getDiameter() + 0.5*other.getDiameter() + 0.00011) 
                return true;
        }
        Iterator<Pherome> pherome_it = nearby_pheromes.iterator();
        while (pherome_it.hasNext()) {
            Pherome other = pherome_it.next();
            if (other.player != player_cell.player && destination.distance(other.getPosition()) < 0.5*player_cell.getDiameter() + 0.0001) 
                return true;
        }
        return false;
    }

    // convert an angle (in 2-deg increments) to a vector with magnitude Cell.move_dist (max allowed movement distance)
    private Point extractVectorFromAngle(int arg) {
        double theta = Math.toRadians( 2* (double)arg );
        double dx = Cell.move_dist * Math.cos(theta);
        double dy = Cell.move_dist * Math.sin(theta);
        return new Point(dx, dy);
    }

}
