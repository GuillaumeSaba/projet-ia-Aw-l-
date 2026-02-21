package awele.bot.competitor.GuiliasBot;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.util.Arrays;

public class GuiliasBot extends CompetitorBot {
    
    private static final int DEFAULT_DEPTH = 6;
    private static final int ENDGAME_DEPTH = 10;
    private static final int ENDGAME_SEED_THRESHOLD = 20;

    public GuiliasBot() throws InvalidBotException {
        this.setBotName("GuiliasBot");
        this.addAuthor("Guillaume SABATIER");
    }

    @Override
    public void initialize() {
        
    }

    @Override
    public void finish() {
    }

    @Override
    public void learn() {
       
    }
    
    @Override
    public double[] getDecision(Board board) {
        double[] decisions = new double[Board.NB_HOLES];
        int currentPlayer = board.getCurrentPlayer();
        boolean[] validMoves = board.validMoves(currentPlayer);
        
        int depth = (board.getNbSeeds() < ENDGAME_SEED_THRESHOLD) ? ENDGAME_DEPTH : DEFAULT_DEPTH;
        
        for (int i = 0; i < Board.NB_HOLES; i++) {
            if (validMoves[i]) {
                try {
                    double[] moveDecision = new double[Board.NB_HOLES];
                    moveDecision[i] = 1.0;
                    Board nextState = board.playMoveSimulationBoard(currentPlayer, moveDecision);
                    double val = minimax(nextState, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, currentPlayer);
                    decisions[i] = val;
                } catch (InvalidBotException e) {
                    decisions[i] = Double.NEGATIVE_INFINITY;
                }
            } else {
                decisions[i] = Double.NEGATIVE_INFINITY;
            }
        }

        return decisions;
    }
    
    private double minimax(Board board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex) {
        if (depth == 0 || board.getNbSeeds() < 2) {
            return evaluate(board, myPlayerIndex);
        }

        int currentPlayer = board.getCurrentPlayer();
        boolean[] validMoves = board.validMoves(currentPlayer);
        
        boolean hasMove = false;
        for (boolean b : validMoves) {
            if (b) {
                hasMove = true;
                break;
            }
        }
        
        if (!hasMove) {
            try {
                double[] noMove = new double[Board.NB_HOLES];
                Arrays.fill(noMove, Double.NEGATIVE_INFINITY);
                Board endBoard = board.playMoveSimulationBoard(currentPlayer, noMove);
                return evaluate(endBoard, myPlayerIndex);
            } catch (InvalidBotException e) {
                return evaluate(board, myPlayerIndex);
            }
        }

        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < Board.NB_HOLES; i++) {
                if (validMoves[i]) {
                    try {
                        double[] moveDecision = new double[Board.NB_HOLES];
                        moveDecision[i] = 1.0;
                        Board nextState = board.playMoveSimulationBoard(currentPlayer, moveDecision);
                        
                        double eval = minimax(nextState, depth - 1, alpha, beta, false, myPlayerIndex);
                        maxEval = Math.max(maxEval, eval);
                        alpha = Math.max(alpha, eval);
                        if (beta <= alpha) break; 
                    } catch (InvalidBotException e) {
                        // Ignorer les coups invalides
                    }
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < Board.NB_HOLES; i++) {
                if (validMoves[i]) {
                    try {
                        double[] moveDecision = new double[Board.NB_HOLES];
                        moveDecision[i] = 1.0;
                        Board nextState = board.playMoveSimulationBoard(currentPlayer, moveDecision);
                        
                        double eval = minimax(nextState, depth - 1, alpha, beta, true, myPlayerIndex);
                        minEval = Math.min(minEval, eval);
                        beta = Math.min(beta, eval);
                        if (beta <= alpha) break;
                    } catch (InvalidBotException e) {
                        
                    }
                }
            }
            return minEval;
        }
    }

    private double evaluate(Board board, int playerIndex) {
        int opponentIndex = Board.otherPlayer(playerIndex);
        double scoreDiff = board.getScore(playerIndex) - board.getScore(opponentIndex);
        
        int mySeeds, oppSeeds;
        if (board.getCurrentPlayer() == playerIndex) {
            mySeeds = board.getPlayerSeeds();
            oppSeeds = board.getOpponentSeeds();
        } else {
            mySeeds = board.getOpponentSeeds();
            oppSeeds = board.getPlayerSeeds();
        }

        double seedDiff = (mySeeds - oppSeeds) * 0.5;

        return (scoreDiff * 100.0) + seedDiff;
    }
}