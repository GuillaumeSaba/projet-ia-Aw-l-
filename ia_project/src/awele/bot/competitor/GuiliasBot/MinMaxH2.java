package awele.bot.competitor.GuiliasBot;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.util.Arrays;

public class MinMaxH2 extends CompetitorBot {

    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false; // Drapeau pour signaler que le temps est écoulé

    public MinMaxH2() throws InvalidBotException {
        this.setBotName("MinMaxH2");
        this.addAuthor("Iliass FERCHACH");
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
        double[] bestDecisions = new double[Board.NB_HOLES];
        Arrays.fill(bestDecisions, Double.NEGATIVE_INFINITY); // Sécurité au cas où

        long startTime = System.currentTimeMillis();
        timeOut = false;

        int currentPlayer = board.getCurrentPlayer();
        boolean[] validMoves = board.validMoves(currentPlayer);

        // L'Approfondissement Itératif (on boucle sur la profondeur)
        // On peut aller jusqu'à 30 théoriquement si l'ordi est surpuissant
        for (int depth = 1; depth <= 30; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            boolean searchCompleted = true; // Est-ce qu'on a fini cette profondeur sans timeout ?

            for (int i = 0; i < Board.NB_HOLES; i++) {
                if (validMoves[i]) {
                    try {
                        double[] moveDecision = new double[Board.NB_HOLES];
                        moveDecision[i] = 1.0;
                        Board nextState = board.playMoveSimulationBoard(currentPlayer, moveDecision);

                        // On passe le startTime au minimax
                        double val = minimax(nextState, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, currentPlayer, startTime);

                        // Si le minimax a déclenché l'alerte de temps, on avorte !
                        if (timeOut) {
                            searchCompleted = false;
                            break;
                        }
                        currentDecisions[i] = val;

                    } catch (InvalidBotException e) {
                        currentDecisions[i] = Double.NEGATIVE_INFINITY;
                    }
                } else {
                    currentDecisions[i] = Double.NEGATIVE_INFINITY;
                }
            }

            // Si on a réussi à finir cette profondeur AVANT la fin du chrono
            if (searchCompleted) {
                // On sauvegarde ces décisions comme étant nos meilleures "certitudes"
                System.arraycopy(currentDecisions, 0, bestDecisions, 0, Board.NB_HOLES);
            } else {
                // Le chrono a sonné pendant qu'on explorait. On jette les calculs en cours,
                // on casse la boucle, et on utilisera la profondeur précédente !
                break;
            }
        }

        return bestDecisions;
    }

    private double minimax(Board board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        // 1. VÉRIFICATION DU CHRONO
        // Si on a dépassé notre temps limite, on lève le drapeau et on sort tout de suite !
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            timeOut = true;
            return 0; // Valeur poubelle, elle sera jetée par le getDecision de toute façon
        }

        // 2. CONDITION D'ARRÊT CLASSIQUE
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

                        double eval = minimax(nextState, depth - 1, alpha, beta, false, myPlayerIndex, startTime);
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

                        double eval = minimax(nextState, depth - 1, alpha, beta, true, myPlayerIndex, startTime);
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

        // Extraction des trous (on doit vérifier à qui est le tour dans ce plateau virtuel)
        int[] myHoles = (board.getCurrentPlayer() == playerIndex) ? board.getPlayerHoles() : board.getOpponentHoles();
        int[] oppHoles = (board.getCurrentPlayer() == playerIndex) ? board.getOpponentHoles() : board.getPlayerHoles();

        int score = 25 * (board.getScore(playerIndex) - board.getScore(opponentIndex));

        for (int i = 0; i < Board.NB_HOLES; i++) {
            int nbGraines = myHoles[i];
            int nbGrainesOpp = oppHoles[i];

            if (nbGraines > 12) {
                score += 28;
            } else if (nbGraines == 0) {
                score -= 54;
            } else if (nbGraines < 3) {
                score -= 36;
            }

            if (nbGrainesOpp > 12) {
                score -= 28;
            } else if (nbGrainesOpp == 0) {
                score += 54;
            } else if (nbGrainesOpp < 3) {
                score += 36;
            }
        }

        return score;
    }
}