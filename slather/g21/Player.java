package slather.g21;

import slather.sim.Cell;
import slather.sim.Point;
import slather.sim.Move;
import slather.sim.Pherome;
import java.util.*;

import slather.util.MinAndArgMin;

public class Player implements slather.sim.Player {

    private Random gen;
    private double vision;
    private int tailLength;
    private double radius;
    private double dTheta;
    private double size = 100;
    private final double PROB_CIRCLE = 0.5;
    private final double RANDOM_STEP_SIZE = 0.75;

    public void init(double d, int t, int side_length) {
        gen = new Random(System.currentTimeMillis());
        vision = d;
        tailLength = t;
        radius = 2 * tailLength / (2*Math.PI);
        dTheta = 1 / radius;
        size = side_length;
    }

    // PURE STRATEGIES

    public Move playCircle(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        double theta = byte2angle( memory );
        double nextTheta = normalizeAngle(theta + dTheta,0);
        byte nextMemory = 0;
        Point vector = null;
        // Try to go any of four normal directions.
        for(int i=0; i<4; ++i) {
            nextMemory = angle2byte( nextTheta, memory );
            // System.out.println("memory,angles " +memory+","+nextMemory+"," +theta+","+nextTheta);
            vector = angle2vector( nextTheta);
            if (!collides( player_cell, vector, nearby_cells, nearby_pheromes))
                return new Move(vector, nextMemory);
            nextTheta += Math.PI/2;
        }

        // if all tries fail, just chill in place
        return new Move(new Point(0,0), (byte)1);
    }

    public Move playScout(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        memory = (byte) (memory& (~ROLE_CIRCLE));
        double acc_x = 0, acc_y = 0;
        Point position = player_cell.getPosition();
        double radius = player_cell.getDiameter() * 0.5;
        for (Cell cell : nearby_cells) {
            Point p = cell.getPosition();
            double dx = p.x - position.x, dy = p.y - position.y;
            if (Math.abs(dx) > Math.abs(p.x + size - position.x)) dx = p.x + size - position.x;
            if (Math.abs(dx) > Math.abs(p.x - size - position.x)) dx = p.x - size - position.x;
            if (Math.abs(dy) > Math.abs(p.y + size - position.y)) dy = p.y + size - position.y;
            if (Math.abs(dy) > Math.abs(p.y - size - position.y)) dy = p.y - size - position.y;

            acc_x -= dx; acc_y -= dy;
        }
        for (Pherome pherome : nearby_pheromes) {
            Point p = pherome.getPosition();
            double dx = p.x - position.x, dy = p.y - position.y;
            if (Math.abs(dx) > Math.abs(p.x + size - position.x)) dx = p.x + size - position.x;
            if (Math.abs(dx) > Math.abs(p.x - size - position.x)) dx = p.x - size - position.x;
            if (Math.abs(dy) > Math.abs(p.y + size - position.y)) dy = p.y + size - position.y;
            if (Math.abs(dy) > Math.abs(p.y - size - position.y)) dy = p.y - size - position.y;

            acc_x -= dx; acc_y -= dy;
        }

        //if (width > 0) 
        if (Math.abs(acc_x) < 1e-7 && Math.abs(acc_y) < 1e-7) acc_x = 1;
        double t = Math.hypot(acc_x, acc_y);
        return new Move(new Point(acc_x / t, acc_y / t), memory);
    }

    public Move chill(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        return new Move(new Point(0,0), memory);
    }

    public Move playRandom(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        double theta;
        Point step;
        for(int i=0; i<10; ++i) {
            theta = TWOPI*gen.nextDouble();
            step = angle2vector(theta);
            // System.out.println(theta + "," + step.x + "," + step.y);
            step = scalarMult(step, RANDOM_STEP_SIZE);
            if (!collides( player_cell, step, nearby_cells, nearby_pheromes))
                    return new Move(step, memory);
        }
        return chill(player_cell, memory, nearby_cells, nearby_pheromes);
    }

    // OVERALL STRATEGY
    // We may introduce more overall strategies and use one depending on initial conditions

    public Move play(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        if (player_cell.getDiameter() >= 2){
            return new Move(true, memory, memory);
        } 
        return playG1(player_cell, memory, nearby_cells, nearby_pheromes);
    }

    //by default goes scout;
    //everytime we split there's a p_circle chance we go circle; 
    public Move play1(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        double theta = byte2angle( memory );
        // reproduce whenever possible
        if (player_cell.getDiameter() >= 2){
            byte memory2 = angle2byte( normalizeAngle(theta + Math.PI,0), memory );
            if(!byteIsCircle(memory) && (gen.nextDouble() < PROB_CIRCLE)) {
                //System.out.println("Generating a circle");
                memory2 = (byte) (memory2|ROLE_CIRCLE);
            } else {
                //System.out.println("Generating a scout");
                memory2 = (byte) (memory2|ROLE_SCOUT);
            }
            return new Move(true, memory, memory2);
        } 

        //if surrounding is too crowded, play circle
        boolean nextCircle = byteIsCircle(memory);
        if(crowded(player_cell,nearby_cells)) nextCircle = true;
        if(!nextCircle) 
            return playScout(player_cell, memory, nearby_cells, nearby_pheromes);
        return playFollow(player_cell, memory, nearby_cells, nearby_pheromes);
    }

    // UTILITY FUNCTIONS, CAN BE USED BY ONE OR MORE STRATEGIES

    //return true if there are more than one nearby friendly cell
    private boolean crowded(Cell player_cell, Set<Cell> nearby_cells){
        Iterator<Cell> itr = nearby_cells.iterator();
        int count = 0;
        while(itr.hasNext()){
            Cell c = itr.next();
            if(c.player == player_cell.player) count++;
        }
        return count >= 1;
    }

    // check if moving player_cell by vector collides with any nearby cell or hostile pherome
    private boolean collides(Cell player_cell, Point vector, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        return collides( player_cell, vector, nearby_cells, nearby_pheromes, 0);
    }

    private boolean collides(Cell player_cell, Point vector, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes, double safe_distance) {
        Iterator<Cell> cell_it = nearby_cells.iterator();
        Point destination = player_cell.getPosition().move(vector);
        while (cell_it.hasNext()) {
            Cell other = cell_it.next();
            if ( destination.distance(other.getPosition()) < 0.5*player_cell.getDiameter() + 0.5*other.getDiameter() + 0.00011 + safe_distance) 
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

    private static final int ANGLE_BITS = 5;
    private static final int ANGLE_MIN = 0;
    private static final int ANGLE_MAX = 1 << ANGLE_BITS;
    private static final int ANGLE_MASK = ANGLE_MAX - 1;

    private static final int ROLE_BITS = 2;
    private static final int ROLE_MIN = ANGLE_MAX;
    private static final int ROLE_MAX = ROLE_MIN + ( 1<<ROLE_BITS );
    private static final int ROLE_MASK = (ROLE_MAX - 1) & ~ANGLE_MASK;
    private static final int ROLE_SCOUT = 0 << ANGLE_BITS;
    private static final int ROLE_CIRCLE = 1 << ANGLE_BITS;
    private static final int ROLE_LEADER = 2 << ANGLE_BITS;
    private static final int ROLE_FOLLOW = 3 << ANGLE_BITS;

    private boolean byteIsCircle(byte b){
        //System.out.printf("%d %d %d\n",b,ROLE_MASK,ROLE_CIRCLE);
        return (b&ROLE_MASK)==ROLE_CIRCLE;
    }

    public Move playFollow(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
        Point self_pos = player_cell.getPosition();
        Point other_pos = null;
        Point difference = null;
        MinAndArgMin<Point> nearest_friendly_cell_distance = new MinAndArgMin<Point>();
        // Cell other = null;
        for(Cell other : nearby_cells) {
            if(other.player == player_cell.player ) {
                other_pos = other.getPosition();
                difference = getDistance(other_pos, self_pos);
                nearest_friendly_cell_distance.consider((float) -Math.hypot(difference.x, difference.y), difference);
            }
        }
        difference = nearest_friendly_cell_distance.argMin;
        if( difference!=null ) {
            difference = unitVector(difference);
            difference = toRight( difference );
            if (!collides( player_cell, difference, nearby_cells, nearby_pheromes, Math.min(vision/3, tailLength) )) {
                            return new Move(difference, memory);
            }
        }

        return playRandom(player_cell, memory, nearby_cells, nearby_pheromes);
    }

    public Move playG1(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {

        Point self_pos = player_cell.getPosition();
        Point other_pos = null;
        Point difference = null;
        float MIN_DISTANCE = tailLength;
        List<Point> differences = new ArrayList<Point>();

        for(Cell other : nearby_cells) {
            other_pos = other.getPosition();
            difference = getDistance(other_pos, self_pos);
            if( Math.hypot( difference.x, difference.y ) <= MIN_DISTANCE ) {
                differences.add( difference );
            }
        }

        difference = new Point(0,0);
        for(Point p : differences) {
            difference = vectorAdd(difference, p);
        }

        difference = scalarMult(difference, -1);

        if( vectorLength(difference) < 0.001 ) {
            return playCircle(player_cell, memory, nearby_cells, nearby_pheromes);
        } else {
            difference = unitVector( difference );
            return new Move(difference, memory);
        }

    }

    private Point toRight(Point dir) {
        if( dir.x < 0 ) {
            return new Point(-dir.x, -dir.y);
        }
        return dir;
    }

    private Point getDistance(Point p, Point position) {

        double dx = p.x - position.x, dy = p.y - position.y;
        if (Math.abs(dx) > Math.abs(p.x + size - position.x)) dx = p.x + size - position.x;
        if (Math.abs(dx) > Math.abs(p.x - size - position.x)) dx = p.x - size - position.x;
        if (Math.abs(dy) > Math.abs(p.y + size - position.y)) dy = p.y + size - position.y;
        if (Math.abs(dy) > Math.abs(p.y - size - position.y)) dy = p.y - size - position.y;

        return new Point(dx, dy);

    }

    private static final double TWOPI = 2*Math.PI;

    private double byte2angle(byte b) {
        // -128 <= b < 128
        // -1 <= b/128 < 1
        // -pi <= a < pi
        return normalizeAngle(TWOPI * (((double) ((b) & ANGLE_MASK) ) / ANGLE_MAX), 0);
    }

    private byte angle2byte(double a, byte b) {
        final double actualAngle = ((normalizeAngle(a,0) / TWOPI)*ANGLE_MAX);
        final int anglePart = (int) (((int)actualAngle) & ANGLE_MASK);
        final byte memoryPart = (byte) ( b & ~ANGLE_MASK);
        // System.out.println("angle2byte "+ memoryPart +","+ anglePart +","+ (normalizeAngle(a,0)/TWOPI) +","+ ANGLE_MAX +","+ a);
        return (byte) ((anglePart | memoryPart));
    }

    private double normalizeAngle(double a, double start) {
        if( a < start ) {
            return normalizeAngle( a+TWOPI, start);
        } else if (a >= (start+TWOPI)) {
            return normalizeAngle( a-TWOPI, start);
        } else {
            return a;
        }
    }


    private Point angle2vector(double a) {
        double dx = Cell.move_dist * Math.cos(a);
        double dy = Cell.move_dist * Math.sin(a);
        return new Point(dx, dy);
    }

    private double vectorLength(Point p) {
        return Math.hypot(p.x, p.y);
    }

    private Point scalarMult(Point a, double t) {
        return new Point(a.x*t, a.y*t);
    }

    private Point vectorAdd(Point a, Point b) {
        return new Point(a.x+b.x, a.y+b.y);
    }

    private Point unitVector(Point a) {
        double t = vectorLength(a);
        return scalarMult(a, 1/t);
    }

    // convert an angle (in 2-deg increments) to a vector with magnitude Cell.move_dist (max allowed movement distance)
    private Point extractVectorFromAngle(int arg) {
        double theta = Math.toRadians( 2* (double)arg );

        double dx = Cell.move_dist * Math.cos(theta + dTheta);
        double dy = Cell.move_dist * Math.sin(theta + dTheta);
        return new Point(dx, dy);
    }

}
