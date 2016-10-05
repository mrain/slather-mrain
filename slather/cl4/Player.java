package slather.cl4;

import slather.sim.Cell;
import slather.sim.Point;
import slather.sim.Move;
import slather.sim.Pherome;
import java.util.*;
import java.lang.*;

// Weight: 1 / distance^2


public class Player implements slather.sim.Player {

	private final double size = 100;

    private Random gen;
    private double vision;
    private int fade;

    public void init(double d, int t) {
        gen = new Random();
        vision = d;
        fade = t;
    }

    public Move play(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        if (player_cell.getDiameter() >= 2) // reproduce whenever possible
            return new Move(true, (byte)-1, (byte)-1);
        Point position = player_cell.getPosition();
        double radius = player_cell.getDiameter() * 0.5;
        Point direction = new Point(0, 0);
        for (Cell cell : nearby_cells) {
            Point p = cell.getPosition();
            double r = cell.getDiameter() * 0.5 + radius;
            double d = position.distance(p);

            Point dir = correctedSubtract(p, position);

            dir = normalize(dir);
            if (Math.abs(d) > 1e-7)
                dir = multiply(dir, 1.0 / (d * d));

            direction = add(direction, multiply(dir, -1));
        }
        for (Pherome pherome : nearby_pheromes) {
            Point p = pherome.getPosition();
            double r = radius;
            double d = position.distance(p);
            
            Point dir = correctedSubtract(p, position);

            dir = normalize(dir);
            if (Math.abs(d) > 1e-7)
                dir = multiply(dir, 1.0 / (d * d));

            direction = add(direction, multiply(dir, -1));
        }
        if (direction.norm() < 1e-8) {
        	double angle = gen.nextDouble() * 2 * Math.PI - Math.PI;
            //double angle = 0;
            direction = new Point(Math.cos(angle), Math.sin(angle));
        }
        direction = normalize(direction);
        return new Move(direction, (byte)0);
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
        if (Math.abs(x) > Math.abs(a.x - size - b.x)) x = a.x + size - b.x;
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
