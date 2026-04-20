package agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Démonstration d'un agent réactif avec son environnement.
 * Regroupe l'interface Agent, l'implémentation ReactiveAgent, les énumérations
 * Action/Perception, la classe Position et l'Environment.
 */
public class AgentDemo {

    public enum Action {
        MOVE_FORWARD, TURN_RIGHT, MOVE_TOWARDS_FOOD, FLEE, DO_NOTHING
    }

    public enum Perception {
        OBSTACLE_AHEAD, FOOD_NEARBY, DANGER, NOTHING_SPECIAL
    }

    public static class Position {
        private int x, y;
        private int orientation;

        public Position(int x, int y) { this.x = x; this.y = y; this.orientation = 0; }
        public int getX() { return x; }
        public int getY() { return y; }

        public Position moveForward() {
            int nx = x, ny = y;
            switch (orientation) {
                case 0: ny--; break;
                case 1: nx++; break;
                case 2: ny++; break;
                case 3: nx--; break;
            }
            Position p = new Position(nx, ny); p.orientation = orientation; return p;
        }

        public Position turnRight() {
            Position p = new Position(x, y); p.orientation = (orientation + 1) % 4; return p;
        }

        public Position moveTowards(Position target) {
            int nx = x + Integer.signum(target.x - x);
            int ny = y + Integer.signum(target.y - y);
            return new Position(nx, ny);
        }

        public Position moveAwayFrom(Position source) {
            int nx = x - Integer.signum(source.x - x);
            int ny = y - Integer.signum(source.y - y);
            return new Position(nx, ny);
        }
    }

    public interface Agent {
        void perceive(Environment environment);
        Action decide();
        void act(Action action, Environment environment);

        default void step(Environment environment) {
            perceive(environment);
            act(decide(), environment);
        }
    }

    public static class ReactiveAgent implements Agent {
        private Position position;
        private final List<Perception> perceptions = new ArrayList<>();
        private final Map<Perception, Action> rules = new HashMap<>();

        public ReactiveAgent(Position initialPosition) {
            this.position = initialPosition;
            rules.put(Perception.OBSTACLE_AHEAD, Action.TURN_RIGHT);
            rules.put(Perception.FOOD_NEARBY, Action.MOVE_TOWARDS_FOOD);
            rules.put(Perception.DANGER, Action.FLEE);
            rules.put(Perception.NOTHING_SPECIAL, Action.MOVE_FORWARD);
        }

        @Override
        public void perceive(Environment environment) {
            perceptions.clear();
            perceptions.addAll(environment.getPerceptionsAt(position));
        }

        @Override
        public Action decide() {
            for (Perception p : perceptions) {
                if (rules.containsKey(p)) return rules.get(p);
            }
            return Action.DO_NOTHING;
        }

        @Override
        public void act(Action action, Environment environment) {
            switch (action) {
                case MOVE_FORWARD: position = position.moveForward(); break;
                case TURN_RIGHT: position = position.turnRight(); break;
                case MOVE_TOWARDS_FOOD: position = position.moveTowards(environment.findNearestFood(position)); break;
                case FLEE: position = position.moveAwayFrom(environment.findNearestDanger(position)); break;
                default: break;
            }
            environment.updateAgentPosition(this, position);
        }

        public Position getPosition() { return position; }
    }

    public static class Environment {
        private final int width, height;
        private final Cell[][] grid;
        private final Map<Agent, Position> agentPositions = new HashMap<>();

        public Environment(int width, int height) {
            this.width = width;
            this.height = height;
            this.grid = new Cell[width][height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) grid[x][y] = new Cell();
            }
        }

        public void addAgent(Agent agent, Position position) {
            if (isValidPosition(position)) agentPositions.put(agent, position);
        }

        public boolean isValidPosition(Position p) {
            return p.getX() >= 0 && p.getX() < width && p.getY() >= 0 && p.getY() < height;
        }

        public void updateAgentPosition(Agent agent, Position newPosition) {
            if (isValidPosition(newPosition)) agentPositions.put(agent, newPosition);
        }

        public List<Perception> getPerceptionsAt(Position position) {
            List<Perception> perceptions = new ArrayList<>();
            if (!isValidPosition(position)) return perceptions;

            Cell cell = grid[position.getX()][position.getY()];
            if (cell.hasObstacle) perceptions.add(Perception.OBSTACLE_AHEAD);
            if (cell.hasFood) perceptions.add(Perception.FOOD_NEARBY);
            if (cell.hasDanger) perceptions.add(Perception.DANGER);
            if (perceptions.isEmpty()) perceptions.add(Perception.NOTHING_SPECIAL);
            return perceptions;
        }

        public Position findNearestFood(Position position) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (grid[x][y].hasFood) return new Position(x, y);
                }
            }
            return position;
        }

        public Position findNearestDanger(Position position) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (grid[x][y].hasDanger) return new Position(x, y);
                }
            }
            return position;
        }

        public void step() {
            List<Agent> agents = new ArrayList<>(agentPositions.keySet());
            for (Agent agent : agents) agent.step(this);
        }

        public void setFoodAt(int x, int y) { grid[x][y].hasFood = true; }
        public void setDangerAt(int x, int y) { grid[x][y].hasDanger = true; }
        public void setObstacleAt(int x, int y) { grid[x][y].hasObstacle = true; }

        private static class Cell {
            boolean hasObstacle = false;
            boolean hasFood = false;
            boolean hasDanger = false;
        }
    }

    public static void main(String[] args) {
        Environment env = new Environment(10, 10);
        env.setFoodAt(5, 5);
        ReactiveAgent agent = new ReactiveAgent(new Position(0, 0));
        env.addAgent(agent, agent.getPosition());

        for (int i = 0; i < 20; i++) {
            env.step();
            System.out.println("Pas " + i + ": agent en (" + agent.getPosition().getX() + "," + agent.getPosition().getY() + ")");
        }
    }
}
