package slather.g3;

import slather.sim.Cell;
import slather.sim.Point;
import slather.sim.Move;
import slather.sim.Pherome;
import java.util.*;

public class Player implements slather.sim.Player {

	private Random gen;
    private double d;
    private int t;
    private int side_length;
    private final int CLOSE_RANGE_VISION = 3;
    private final int ANGLE_PRECISION = 2;

    public void init(double d, int t, int side_length) {
		gen = new Random();
		this.d = d;
		this.t = t;
		this.side_length = side_length;
    }

	public Move play(Cell player_cell, byte memory, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {			
		
		if (player_cell.getDiameter() >= 2){ // reproduce whenever possible			
			// Use code below if want to have different first 6 bits and last 2 bits
			//byte memory1 = (byte) ((f6bits << 2) | (0x03 & l2bits)); //First daughter keeps same strategy
			//byte memory2 = (byte) ((f6bits << 2) | (0x03 & (l2bits)));
			//byte memory1 = writeMemoryByte(f6bits, l2bits);
			//byte memory2 = writeMemoryByte(f6bits, 0);
			
			byte memory1 = memory;
			int angle2 = memoryToAngleInt(memory);
			angle2 = (angle2 + 180)%360; //Angle should be opposite 
			byte memory2 =  angleToByte(angle2);
			
			return new Move(true, memory1, memory2);
		}

		ArrayList<Integer> cellAngleList = generateAngleListOfNearbyCells(player_cell,nearby_cells, false);
		ArrayList<Integer> pheromeAngleList = generateAngleListOfNearbyPheromes(player_cell,nearby_pheromes, true);
		ArrayList<Integer> angleList = new ArrayList<Integer>();
		angleList.addAll(cellAngleList);
		angleList.addAll(pheromeAngleList);
		Collections.sort(angleList);
//			System.out.println(angleList);
		
		if(angleList.isEmpty()){
			int currentAngle = memoryToAngleInt(memory);
			Point vector = extractVectorFromAngle(currentAngle);
			if (!collides(player_cell, vector, nearby_cells, nearby_pheromes)){
				return new Move(vector, memory);
			}
		}
		else if(angleList.size()==1){
			int currentAngle = (angleList.get(0)+180)%360;
			Point vector = extractVectorFromAngle(currentAngle);
			if (!collides(player_cell, vector, nearby_cells, nearby_pheromes)){
				memory = angleToByte(currentAngle);
				return new Move(vector, memory);
			}
		}
		else{
			LinkedList<Integer> possibleAngles = new LinkedList<Integer>();
			// first take care of special case between first and last cell
			int maxDiff = angleList.get(0)-angleList.get(angleList.size()-1)+360;
			int bisectAngle = (angleList.get(angleList.size()-1) + maxDiff/2)%360;
			possibleAngles.addFirst(bisectAngle);
			
			for(int i=0; i<angleList.size()-1; i++){
				//Don't consider if causes collision
				bisectAngle = ((angleList.get(i+1) + angleList.get(i)) % 360) / 2; 
				if (collides(player_cell, extractVectorFromAngle(bisectAngle), nearby_cells, nearby_pheromes)){
					continue;
				}
				
				int thisDiff = angleList.get(i+1) - angleList.get(i);
				if(	thisDiff > maxDiff	){
					possibleAngles.addFirst(bisectAngle); // sorting angles list from best to worst
					maxDiff = thisDiff;
				}
			}
			for(int j=0; j<possibleAngles.size(); j++){
				int best_angle = possibleAngles.get(j);
				Point vector = extractVectorFromAngle(best_angle);
				if (!collides(player_cell, vector, nearby_cells, nearby_pheromes)){
					memory = angleToByte(best_angle); // keep within 8 bits
					return new Move(vector, memory);
				}
			}
		}	
	
		// If there was a collision, try a few random directions to go in until one doesn't collide
		for (int i = 0; i < 10; i++) {
			int rand_angle = gen.nextInt(360);
			Point rand_vector = extractVectorFromAngle(rand_angle);
			if (!collides(player_cell, rand_vector, nearby_cells, nearby_pheromes)){
				memory = angleToByte(rand_angle);
				return new Move(rand_vector, memory);
			}
		}

		// If no successful random direction, try reversing
		int reverseAngle = (memoryToAngleInt(memory) + 180)%360;
		memory = angleToByte(reverseAngle);
		Point rev_vector = extractVectorFromAngle(reverseAngle);
		if (!collides(player_cell, rev_vector, nearby_cells, nearby_pheromes)){
			return new Move(rev_vector, memory);
		}

		// if all tries fail, just chill in place
		return new Move(new Point(0, 0), (byte) 0);
	}

	// check if moving player_cell by vector collides with any nearby cell or
	// hostile pherome
	private boolean collides(Cell player_cell, Point vector, Set<Cell> nearby_cells, Set<Pherome> nearby_pheromes) {
		Iterator<Cell> cell_it = nearby_cells.iterator();
		Point destination = player_cell.getPosition().move(vector);
		while (cell_it.hasNext()) {
			Cell other = cell_it.next();
			if (destination.distance(other.getPosition()) < 0.5 * player_cell.getDiameter() + 0.5 * other.getDiameter()
					+ 0.00011)
				return true;
		}
		Iterator<Pherome> pherome_it = nearby_pheromes.iterator();
		while (pherome_it.hasNext()) {
			Pherome other = pherome_it.next();
			if (other.player != player_cell.player
					&& destination.distance(other.getPosition()) < 0.5 * player_cell.getDiameter() + 0.0001)
				return true;
		}
		return false;
	}

	// convert an angle (in 2-deg increments) to a vector with magnitude
	// Cell.move_dist (max allowed movement distance)
	private Point extractVectorFromAngle(int arg) {
		
		double theta = Math.toRadians(1 * (double) arg); //We need bigger circles!
		double dx = Cell.move_dist * Math.cos(theta);
		double dy = Cell.move_dist * Math.sin(theta);
		return new Point(dx, dy);
	}
	
    private Set<Cell> closeRangeCells(Cell source_cell, Set<Cell> all_cells) {
    	Set<Cell> closest_cells = new HashSet<Cell>();
		for (Cell other_cell : all_cells) {
			if (source_cell.distance(other_cell) < CLOSE_RANGE_VISION) {
				closest_cells.add(other_cell);
			}
		}
		return closest_cells;
    }
    
	private int readf8Bits(byte memory){
		int f8bits = (memory & 0xff); 
		return f8bits;
	}
	
	private byte writeMemoryByte(int f8bits){
		if(f8bits < 0){
			throw new RuntimeException("f8bits is negative: [" + f8bits + "]");
		}
		if(f8bits > 255){
			throw new RuntimeException("f8bits is greater than 255: [" + f8bits + "]");
		}
		byte memory = (byte) ((f8bits & 0xff));
		return memory;
	}

	private int memoryToAngleInt(byte memory){
		return ANGLE_PRECISION * readf8Bits(memory);
	}
	
	private byte angleToByte(int angle){
		int intToWrite = angle/ANGLE_PRECISION;
		if(intToWrite < 0){
			throw new RuntimeException("angle/ANGLE_PRECISION is negative: [" + intToWrite + "]");
		}
		if(intToWrite > 255){
			throw new RuntimeException("angle/ANGLE_PRECISION is greater than 255: [" + intToWrite + "]");
		}
		return (byte) (intToWrite & 0xff);
	}
	
	
	//Threshold is negative if we want to ignore it
 	private TreeMap<Integer, Cell> generateMapOfNearbyCells(Cell player_cell, Set<Cell> nearby_cells, boolean ignoreSamePlayer, float threshold){
		TreeMap<Integer, Cell> angleToCellMap = new TreeMap<Integer, Cell>();
		for(Cell c : nearby_cells){
			if(ignoreSamePlayer && (c.player == player_cell.player)){
				continue;
			}
			if( (threshold > 0)  && (player_cell.distance(c) > threshold)	){
				continue;
			}
			
			double cX = c.getPosition().x;
			double cY = c.getPosition().y;
			double tX = player_cell.getPosition().x;
			double tY = player_cell.getPosition().y;
			double dX = cX-tX;
			double dY = cY-tY;
			double angle = Math.atan(dY/dX);
			if(dX>=0 && dY>=0); //Do nothing
			if(dX>=0 && dY<0) angle += 2*Math.PI;
			if(dX<0 && dY>=0) angle += Math.PI;
			if(dX<0 && dY<0) angle += Math.PI;
			angleToCellMap.put((int)Math.toDegrees(angle), c);
		}
		return angleToCellMap;
	}
	//If threshold is negative, we ignore it
	private TreeMap<Integer, Pherome> generateMapOfNearbyPheromes(Cell player_cell, Set<Pherome> nearby_pheromes, boolean ignoreSamePlayer, float threshold){
		TreeMap<Integer, Pherome> angleToPheromeMap = new TreeMap<Integer, Pherome>();
		for(Pherome p : nearby_pheromes){
			if(ignoreSamePlayer && (p.player == player_cell.player)){
				continue;
			}
			if( (threshold > 0)  && (player_cell.distance(p) > threshold)	){
				continue;
			}
			
			double cX = p.getPosition().x;
			double cY = p.getPosition().y;
			double tX = player_cell.getPosition().x;
			double tY = player_cell.getPosition().y;
			double dX = cX-tX;
			double dY = cY-tY;
			double angle = Math.atan(dY/dX);
			if(dX>=0 && dY>=0); //Do nothing
			if(dX>=0 && dY<0) angle += 2*Math.PI;
			if(dX<0 && dY>=0) angle = Math.PI - angle;
			if(dX<0 && dY<0) angle = Math.PI - angle;
			angleToPheromeMap.put((int)Math.toDegrees(angle), p);
		}
		return angleToPheromeMap;
	}

	private ArrayList<Integer> generateAngleListOfNearbyCells(Cell player_cell, Set<Cell> nearby_cells, boolean ignoreSamePlayer){
		ArrayList<Integer> angleList = new ArrayList<Integer>();
		for(Cell c : nearby_cells){
			if(ignoreSamePlayer && (c.player == player_cell.player)) { //Ignore your own pheromes
				continue;
			}
			if(player_cell.distance(c) > CLOSE_RANGE_VISION) {
				continue;
			}
			double cX = c.getPosition().x;
			double cY = c.getPosition().y;
			double tX = player_cell.getPosition().x;
			double tY = player_cell.getPosition().y;
			double dX = cX-tX;
			double dY = cY-tY;
			double angle = Math.atan(dY/dX);
			
			if(dX>=0 && dY>=0); //Do nothing
			if(dX>=0 && dY<0) angle += 2*Math.PI;
			if(dX<0 && dY>=0) angle = Math.PI - angle;
			if(dX<0 && dY<0) angle = Math.PI - angle;
//			System.out.println(player_cell.hashCode() + "\t" + dY/dX);
//			System.out.println(Math.toDegrees(angle));
			angleList.add((int)Math.toDegrees(angle));
		}
		Collections.sort(angleList);
		return angleList;
	}
	
	private ArrayList<Integer> generateAngleListOfNearbyPheromes(Cell player_cell, Set<Pherome> nearby_pheromes, boolean ignoreSamePlayer){
		ArrayList<Integer> angleList = new ArrayList<Integer>();
		for(Pherome p : nearby_pheromes){
			if(ignoreSamePlayer && (p.player == player_cell.player)) //Ignore your own pheromes
				continue;
			double pX = p.getPosition().x;
			double pY = p.getPosition().y;
			double tX = player_cell.getPosition().x;
			double tY = player_cell.getPosition().y;
			double dX = pX-tX;
			double dY = pY-tY;
			double angle = Math.atan(dY/dX);
			
			if(dX>=0 && dY>=0); //Do nothing
			if(dX>=0 && dY<0) angle += 2*Math.PI;
			if(dX<0 && dY>=0) angle = Math.PI - angle;
			if(dX<0 && dY<0) angle = Math.PI - angle;
			
//			System.out.println(player_cell.hashCode() + "\t" + dY/dX);
//			System.out.println(Math.toDegrees(angle));
			angleList.add((int)Math.toDegrees(angle));
		}
		Collections.sort(angleList);
		return angleList;
	}
}
