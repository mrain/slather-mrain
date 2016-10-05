package slather.g22;

import slather.sim.Cell;
import slather.sim.Point;
import slather.sim.Move;
import slather.sim.Pherome;
import java.util.*;


public class Player implements slather.sim.Player {
    
    private Random gen;
    private double upper;
    private double lower;
    private double side_length;
    private double d;

    public void init(double d, int t, int side_length) {
		gen = new Random();
		this.side_length = (double)side_length;
		this.lower = 1;
		this.upper = Math.min(d,3);
		this.d = d;
    }

    //steadily increase the occupied area, use no memory
    //each time go to farthest cell friendly away from you without colliding 
    //if not possible, just evade
    public Move play(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
    	//always reproduce when possible
		if(player_cell.getDiameter() >= 2){
			return new Move(true,memory,memory);
		}
		

		//int start = ((int)(memory) + 16)%32;
		//if any choice satisfies the distance bewteen upper and lower bound, take that direction
		for(int i = 0; i < 36; i++){
			int deg = gen.nextInt(360);
			Point direction = extractVectorFromAngle(deg);
			if(!collides(player_cell, direction, nearby_cells, nearby_pheromes) &&
				within_range(direction,player_cell,nearby_cells)){
				//System.out.println("lower: " + min_max[0]);
				//System.out.println("upper: " + min_max[1]);
				//System.out.println("i: " + i);
				System.out.println("cluster");
				System.out.println(deg);
				return new Move(direction,(byte)i);
			}
		}
		
		//if all else fails, return random
		System.out.println("random");

		return random(player_cell,memory,nearby_cells,nearby_pheromes);
    }

    //check whether the min/max distance from ally cells if moving towards this direction is within range
    //define the distance to be from center of current cell to center of ally cell subtract radius of each cell
    private boolean within_range(Point direction, Cell player_cell, Set<Cell> nearby_cells){
		Point curr = player_cell.getPosition();
		Point next = new Point(direction.x + curr.x, direction.y + curr.y);
		boolean found = false;
		double friends = 0;

		for(Cell other:nearby_cells){
			if(other.player == player_cell.player){
				friends++;
			}
		}
		if(friends == 0) return false;

		for(Cell other:nearby_cells){
			if(other.player == player_cell.player){
				double distance = other.getPosition().distance(next) - 0.5 * player_cell.getDiameter() - 0.5 * other.getDiameter();
				double lo = lower * friends / d;
				double hi = upper * friends / d;
				if(distance < lo || distance > hi) return false;
				found = true;
			}
		}
		if(!found){
			//System.out.println("found");
			return false;
		}
		return true;
    }



    public Move playScout(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
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
                dir = multiply(dir, weight(d, r));

            direction = add(direction, multiply(dir, -1));
        }
        for (Pherome pherome : nearby_pheromes) {
            Point p = pherome.getPosition();
            double r = radius;
            double d = position.distance(p);
            
            Point dir = correctedSubtract(p, position);

            dir = normalize(dir);
            if (Math.abs(d) > 1e-7)
                dir = multiply(dir, weight(d, r));

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


    private static double logGamma(double x) {
      double tmp = (x - 0.5) * Math.log(x + 4.5) - (x + 4.5);
      double ser = 1.0 + 76.18009173    / (x + 0)   - 86.50532033    / (x + 1)
                       + 24.01409822    / (x + 2)   -  1.231739516   / (x + 3)
                       +  0.00120858003 / (x + 4)   -  0.00000536382 / (x + 5);
      return tmp + Math.log(ser * Math.sqrt(2 * Math.PI));
   }
   private static double gamma(double x) { return Math.exp(logGamma(x)); }

    private double weight(double dist, double r) {
        final double lambda = 4;

        return Math.pow(lambda, dist) * Math.exp(-lambda) / gamma(dist);
    }

    private Point add(Point a, Point b) {
        return new Point(a.x + b.x, a.y + b.y);
    }

    private Point subtract(Point a, Point b) {
        return new Point(a.x - b.x, a.y - b.y);
    }

    private Point correctedSubtract(Point a, Point b) {
        double x = a.x - b.x, y = a.y - b.y;
        if (Math.abs(x) > Math.abs(a.x + side_length - b.x)) x = a.x + side_length - b.x;
        if (Math.abs(x) > Math.abs(a.x - side_length - b.x)) x = a.x + side_length - b.x;
        if (Math.abs(y) > Math.abs(a.y + side_length - b.x)) y = a.y + side_length - b.y;
        if (Math.abs(y) > Math.abs(a.y - side_length - b.x)) y = a.y - side_length - b.y;
        return new Point(x, y);
    }

    private Point multiply(Point a, double d) {
        return new Point(a.x * d, a.y * d);
    }

    private Point normalize(Point a) {
        if (a.norm() < 1e-7) return a;
        else return multiply(a, 1.0/a.norm());
    }


    //default random move algo
    private Move random(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes){
    	// if no previous direction specified or if there was a collision, try random directions to go in until one doesn't collide
		for (int i=0; i<20; i++) {
		    int arg = gen.nextInt(180)+1;
		    Point vector = extractVectorFromAngle(arg);
		    if (!collides(player_cell, vector, nearby_cells, nearby_pheromes)) 
			return new Move(vector, (byte) arg);
		}
		// if all tries fail, just chill in place
		return new Move(new Point(0,0), (byte)0);
    }

     private Point getDirection(Point a, Point b) {
        double x = a.x - b.x, y = a.y - b.y;
        if (Math.abs(x) > Math.abs(a.x + side_length - b.x)) x = a.x + side_length - b.x;
        if (Math.abs(x) > Math.abs(a.x - side_length - b.x)) x = a.x + side_length - b.x;
        if (Math.abs(y) > Math.abs(a.y + side_length - b.x)) y = a.y + side_length - b.y;
        if (Math.abs(y) > Math.abs(a.y - side_length - b.x)) y = a.y - side_length - b.y;
        double norm = x*x + y*y;
        if(norm != 0) return new Point(x/norm, y/norm);
        return new Point(0,0);
    }

    // check if moving player_cell by vector collides with any nearby cell or hostile pherome
    private boolean collides(Cell player_cell, Point vector, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
		Iterator<Cell> cell_it = nearby_cells.iterator();
		Point destination = player_cell.getPosition().move(vector);
		while (cell_it.hasNext()) {
		    Cell other = cell_it.next();
		    if ( destination.distance(other.getPosition()) < 0.5 * player_cell.getDiameter() + 0.5 * other.getDiameter() + 0.00011) 
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
		double theta = Math.toRadians( (double)arg );
		double dx = Cell.move_dist * Math.cos(theta);
		double dy = Cell.move_dist * Math.sin(theta);
		return new Point(dx, dy);
    }
}
