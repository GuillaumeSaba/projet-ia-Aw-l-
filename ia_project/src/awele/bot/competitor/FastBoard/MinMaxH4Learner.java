package awele.bot.competitor.FastBoard;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MinMaxH4Learner extends CompetitorBot {

    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false;
    
    private int[] weights = new int[10];
    
    private Random random;
    private List<TrainingData> dataset;
    
    private static class TrainingData {
        int[] myHoles;
        int[] oppHoles;
        boolean winning;
    }

    public MinMaxH4Learner() throws InvalidBotException {
        this.setBotName("MinMaxH4Learner");
        this.addAuthor("SABATIER Guillaume");
        this.random = new Random();
        this.dataset = new ArrayList<>();
    }

    @Override
    public void initialize() {
    }

    @Override
    public void finish() {
    }

    @Override
    public void learn() {
        long startTime = System.currentTimeMillis();
        long maxDuration = 3300000; // 55 minutes (remettre 3300000)

        loadDataset("C:/Users/Guillaume/projet-ia-Aw-l-/ia_project/data/awele.data");
        
        int popSize = 20;
        int simulationDepth = 6;
        
        // Poids de référence (MinMaxH4)
        int[] refWeights = {25, 28, -54, -36, 0, 0, 0, 0, 0, 0};
        
        Integer[][] population = new Integer[popSize][11];
        
        // Toute la population commence comme MinMaxH4
        for (int i = 0; i < popSize; i++) {
            population[i] = new Integer[11];
            for (int w = 0; w < 10; w++) {
                population[i][w] = refWeights[w];
            }
            // On applique une mutation très légère dès le début pour diversifier
            if (i > 0) population[i] = mutateGenome(population[i]);
            population[i][10] = 0;
        }
        
        int generation = 0;
        while (System.currentTimeMillis() - startTime < maxDuration) {
            for (int i = 0; i < popSize; i++) {
                int[] w = toIntArray(population[i]);
                
                // 1. Score Dataset (x20)
                int scoreData = evaluateOnDataset(w) * 20;
                
                // 2. Score Duel contre le MAÎTRE (MinMaxH4)
                // On joue 2 matchs (aller-retour) contre les poids de référence
                int scoreDuel = 0;
                
                // Match 1 : Learner commence
                int res1 = playMatch(w, refWeights, 4);
                if (res1 > 0) scoreDuel += 100; // Victoire contre le maître = Jackpot
                else if (res1 == 0) scoreDuel += 20;
                
                // Match 2 : Maître commence
                int res2 = playMatch(refWeights, w, 4); // Attention playMatch retourne score J1 - J2
                // Si res2 < 0, alors J2 (Learner) a gagné
                if (res2 < 0) scoreDuel += 100;
                else if (res2 == 0) scoreDuel += 20;
                
                population[i][10] = scoreData + scoreDuel;
            }
            
            Arrays.sort(population, Comparator.comparingInt(a -> -a[10]));

            if (generation % 5 == 0) {
                System.err.println("Gen " + generation + " Best Score=" + population[0][10] + " Weights=" + Arrays.toString(Arrays.copyOf(population[0], 10)));
            }

            // Reproduction (Élitisme fort)
            Integer[][] newPopulation = new Integer[popSize][11];
            // On garde le meilleur tel quel
            newPopulation[0] = Arrays.copyOf(population[0], 11);
            
            for (int i = 1; i < popSize; i++) {
                // On prend le meilleur et on le mute légèrement
                // On ne fait plus de crossover, on fait de la recherche locale autour du meilleur
                newPopulation[i] = mutateGenome(population[0]);
            }
            population = newPopulation;
            generation++;
        }
        
        weights = toIntArray(population[0]);
        System.err.println("Learning finished. Best weights: " + Arrays.toString(weights));
    }
    
    private void loadDataset(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 14) continue;
                TrainingData data = new TrainingData();
                data.myHoles = new int[6];
                data.oppHoles = new int[6];
                for(int i=0; i<6; i++) data.myHoles[i] = Integer.parseInt(parts[i]);
                for(int i=0; i<6; i++) data.oppHoles[i] = Integer.parseInt(parts[6+i]);
                data.winning = parts[13].equals("G");
                dataset.add(data);
            }
        } catch (IOException e) {}
    }
    
    private int evaluateOnDataset(int[] w) {
        int score = 0;
        for (TrainingData data : dataset) {
            double eval = evaluateSimRaw(data.myHoles, data.oppHoles, 0, 0, w);
            if (data.winning && eval > 0) score += 1;
            else if (!data.winning && eval < 0) score += 1;
        }
        return score;
    }
    
    private int[] toIntArray(Integer[] arr) {
        int[] res = new int[10];
        for(int i=0; i<10; i++) res[i] = arr[i];
        return res;
    }

    private Integer[] mutateGenome(Integer[] genome) {
        Integer[] newGenome = Arrays.copyOf(genome, 11);
        // Mutation très légère : on change 1 seul poids
        int geneIdx = random.nextInt(10);
        
        // Soit +1/-1, soit +/- 5%
        if (random.nextBoolean()) {
            newGenome[geneIdx] += (random.nextBoolean() ? 1 : -1);
        } else {
            double factor = 0.95 + (random.nextDouble() * 0.10); // 0.95 à 1.05
            newGenome[geneIdx] = (int) (newGenome[geneIdx] * factor);
        }
        return newGenome;
    }
    
    private int playMatch(int[] w1, int[] w2, int depth) {
        FastBoard board = new FastBoard();
        int moves = 0;
        while (board.getTotalSeeds() > 6 && moves < 200) {
            int cp = board.currentPlayer;
            int[] cw = (cp == 0) ? w1 : w2;
            int bestMove = -1;
            double bestVal = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double val = minimaxSim(next, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, cp, cw);
                    if (val > bestVal) { bestVal = val; bestMove = i; }
                }
            }
            if (bestMove == -1) break;
            board = board.playMove(bestMove);
            moves++;
        }
        int s1 = board.scores[0];
        int s2 = board.scores[1];
        int seeds = board.getTotalSeeds();
        if (board.currentPlayer == 0) s2 += seeds; else s1 += seeds;
        return Integer.compare(s1, s2);
    }
    
    private double minimaxSim(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, int[] w) {
        if (depth == 0 || board.getTotalSeeds() < 2) return evaluateSim(board, myPlayerIndex, w);
        boolean hasMove = false;
        for(int i=0; i<6; i++) if (board.isValid(i)) { hasMove = true; break; }
        if (!hasMove) {
             int seeds = board.getTotalSeeds();
             int sP = board.scores[myPlayerIndex];
             int sO = board.scores[1-myPlayerIndex];
             if (board.currentPlayer == myPlayerIndex) sO += seeds; else sP += seeds;
             return 10000.0 * (sP - sO);
        }
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, false, myPlayerIndex, w);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, true, myPlayerIndex, w);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            return minEval;
        }
    }
    
    private double evaluateSimRaw(int[] myHoles, int[] oppHoles, int myScore, int oppScore, int[] w) {
        double score = w[0] * (myScore - oppScore);
        int mySeeds = 0, oppSeeds = 0;
        int myMobility = 0, oppMobility = 0;
        for (int i = 0; i < 6; i++) {
            int s = myHoles[i];
            int os = oppHoles[i];
            mySeeds += s; oppSeeds += os;
            if (s > 0) myMobility++;
            if (os > 0) oppMobility++;
            if (s > 12) score += w[1]; else if (s == 0) score += w[2]; else if (s < 3) score += w[3];
            if (os > 12) score -= w[1]; else if (os == 0) score -= w[2]; else if (os < 3) score -= w[3];
        }
        score += w[4] * (mySeeds - oppSeeds);
        score += w[5] * (myMobility - oppMobility);
        if (oppSeeds < 10) score += w[6];
        score += w[7];
        score += w[8] * mySeeds;
        score += w[9] * oppSeeds;
        return score;
    }
    
    private double evaluateSim(FastBoard board, int playerIndex, int[] w) {
        int opponentIndex = 1 - playerIndex;
        int[] myHoles = new int[6];
        int[] oppHoles = new int[6];
        for(int i=0; i<6; i++) {
            myHoles[i] = board.holes[playerIndex * 6 + i];
            oppHoles[i] = board.holes[opponentIndex * 6 + i];
        }
        return evaluateSimRaw(myHoles, oppHoles, board.scores[playerIndex], board.scores[opponentIndex], w);
    }

    @Override
    public double[] getDecision(Board board) {
        if (board.getLog(board.getCurrentPlayer()).length == 0 && board.getLog(Board.otherPlayer(board.getCurrentPlayer())).length == 0) {
            double[] firstMoveDecision = new double[Board.NB_HOLES];
            Arrays.fill(firstMoveDecision, Double.NEGATIVE_INFINITY);
            if (board.validMoves(board.getCurrentPlayer())[5]) {
                firstMoveDecision[5] = 1.0;
                return firstMoveDecision;
            }
        }

        double[] bestDecisions = new double[Board.NB_HOLES];
        Arrays.fill(bestDecisions, Double.NEGATIVE_INFINITY);
        long startTime = System.currentTimeMillis();
        timeOut = false;
        FastBoard rootState = new FastBoard(board);
        int currentPlayer = board.getCurrentPlayer();
        for (int depth = 1; depth <= 50; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            boolean searchCompleted = true;
            for (int i = 0; i < 6; i++) {
                if (rootState.isValid(i)) {
                    FastBoard nextState = rootState.playMove(i);
                    double val = minimax(nextState, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, currentPlayer, startTime);
                    if (timeOut) { searchCompleted = false; break; }
                    currentDecisions[i] = val;
                } else {
                    currentDecisions[i] = Double.NEGATIVE_INFINITY;
                }
            }
            if (searchCompleted) {
                System.arraycopy(currentDecisions, 0, bestDecisions, 0, Board.NB_HOLES);
            } else {
                break;
            }
        }
        return bestDecisions;
    }

    private double minimax(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) { timeOut = true; return 0; }
        if (depth == 0 || board.getTotalSeeds() < 2) return evaluateSim(board, myPlayerIndex, weights);
        boolean hasMove = false;
        for(int i=0; i<6; i++) if (board.isValid(i)) { hasMove = true; break; }
        if (!hasMove) {
             int seeds = board.getTotalSeeds();
             int sP = board.scores[myPlayerIndex];
             int sO = board.scores[1-myPlayerIndex];
             if (board.currentPlayer == myPlayerIndex) sO += seeds; else sP += seeds;
             return 10000.0 * (sP - sO);
        }
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double eval = minimax(next, depth - 1, alpha, beta, false, myPlayerIndex, startTime);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    double eval = minimax(next, depth - 1, alpha, beta, true, myPlayerIndex, startTime);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            return minEval;
        }
    }
    
    private class FastBoard {
        int[] holes = new int[12];
        int[] scores = new int[2];
        int currentPlayer;
        private FastBoard() {}
        public FastBoard(Board b) {
            this.currentPlayer = b.getCurrentPlayer();
            this.scores[0] = b.getScore(0);
            this.scores[1] = b.getScore(1);
            int[] p0 = (this.currentPlayer == 0) ? b.getPlayerHoles() : b.getOpponentHoles();
            int[] p1 = (this.currentPlayer == 1) ? b.getPlayerHoles() : b.getOpponentHoles();
            for (int i = 0; i < 6; i++) { this.holes[i] = p0[i]; this.holes[6 + i] = p1[i]; }
        }
        public int getTotalSeeds() { return 48 - this.scores[0] - this.scores[1]; }
        public boolean isValid(int moveIndex) {
            int pos = this.currentPlayer * 6 + moveIndex;
            if (this.holes[pos] == 0) return false;
            int oppSide = 1 - this.currentPlayer;
            boolean opponentEmpty = true;
            for (int i = 0; i < 6; i++) if (this.holes[oppSide * 6 + i] > 0) { opponentEmpty = false; break; }
            if (opponentEmpty) return (moveIndex + this.holes[pos] >= 6);
            return true;
        }
        public FastBoard playMove(int moveIndex) {
            FastBoard next = new FastBoard();
            System.arraycopy(this.holes, 0, next.holes, 0, 12);
            next.scores[0] = this.scores[0];
            next.scores[1] = this.scores[1];
            next.currentPlayer = this.currentPlayer;
            int side = next.currentPlayer;
            int pos = side * 6 + moveIndex;
            int seeds = next.holes[pos];
            next.holes[pos] = 0;
            int currentHole = pos;
            while (seeds > 0) {
                currentHole = (currentHole + 1) % 12;
                if (currentHole == pos) continue;
                next.holes[currentHole]++;
                seeds--;
            }
            int oppSide = 1 - side;
            if (currentHole / 6 == oppSide && (next.holes[currentHole] == 2 || next.holes[currentHole] == 3)) {
                boolean takeAll = true;
                for (int i = 0; i <= currentHole % 6; i++) {
                    int val = next.holes[oppSide * 6 + i];
                    if (val == 1 || val > 3) { takeAll = false; break; }
                }
                if (takeAll) {
                    for (int i = (currentHole % 6) + 1; i < 6; i++) {
                        if (next.holes[oppSide * 6 + i] != 0) { takeAll = false; break; }
                    }
                }
                if (!takeAll) {
                    while (currentHole >= 0 && currentHole / 6 == oppSide && (next.holes[currentHole] == 2 || next.holes[currentHole] == 3)) {
                        next.scores[side] += next.holes[currentHole];
                        next.holes[currentHole] = 0;
                        currentHole--;
                    }
                }
            }
            next.currentPlayer = 1 - side;
            return next;
        }
    }
}