package awele.bot.competitor.GuiliasBot;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;
import awele.bot.competitor.AwariLong.AwariLong;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
/*
public class MinMaxH2Learner extends CompetitorBot {

    private static final long TIME_LIMIT_MS = 90;
    private boolean timeOut = false;
    
    // Poids simplifiés (10 features) pour éviter l'overfitting
    // 0: Score
    // 1: Krou (>12)
    // 2: Vide (0)
    // 3: Vulnérable (1-2)
    // 4: Différence de graines
    // 5: Mobilité
    // 6: Menace famine
    // 7: Bias
    // 8: Bonus Attaque (Mes graines)
    // 9: Malus Défense (Graines adverses)
    private int[] weights = new int[10];
    
    private Random random;
    
    private static class TrainingData {
        int[] myHoles;
        int[] oppHoles;
        boolean winning;
    }
    
    private List<TrainingData> dataset;

    public MinMaxH2Learner() throws InvalidBotException {
        this.setBotName("MinMaxH2Learner");
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
        long maxDuration = 3300000; // 55 minutes

        loadDataset("C:/Users/Guillaume/projet-ia-Aw-l-/ia_project/data/awele.data");
        
        int popSize = 30;
        int simulationDepth = 6;
        
        // Population: [popSize][10 poids + 1 score]
        Integer[][] population = new Integer[popSize][11];
        
        // Individu 0 : Référence
        population[0] = new Integer[]{25, 28, -54, -36, 1, 1, 10, 0, 1, -1, 0};
        
        for (int i = 1; i < popSize; i++) {
            population[i] = new Integer[11];
            for (int w = 0; w < 10; w++) {
                if (w < 4) population[i][w] = mutate(population[0][w]);
                else population[i][w] = random.nextInt(21) - 10;
            }
            population[i][10] = 0;
        }
        
        int generation = 0;
        while (System.currentTimeMillis() - startTime < maxDuration) {
            // 1. Évaluation Mixte
            for (int i = 0; i < popSize; i++) {
                int[] w = toIntArray(population[i]);
                
                // Score Dataset (x10)
                int scoreData = evaluateOnDataset(w) * 10;
                
                // Score Tournoi (x1)
                int scorePlay = 0;
                for (int j = 0; j < 3; j++) { // 3 matchs rapides
                    int opponentIdx = random.nextInt(popSize);
                    if (i == opponentIdx) continue;
                    int result = playMatch(w, toIntArray(population[opponentIdx]), 4); // Profondeur 4 pour aller vite
                    if (result > 0) scorePlay += 3;
                    else if (result == 0) scorePlay += 1;
                }
                
                population[i][10] = scoreData + scorePlay;
            }
            
            // 2. Sélection
            Arrays.sort(population, Comparator.comparingInt(a -> -a[10]));



            // 3. Reproduction
            Integer[][] newPopulation = new Integer[popSize][11];
            for(int k=0; k<3; k++) newPopulation[k] = Arrays.copyOf(population[k], 11);

            for (int i = 3; i < popSize; i++) {
                Integer[] parent1 = population[random.nextInt(popSize / 2)];
                Integer[] parent2 = population[random.nextInt(popSize / 2)];
                Integer[] child = crossover(parent1, parent2);
                if (random.nextDouble() < 0.20) child = mutateGenome(child);
                newPopulation[i] = child;
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
    
    private Integer[] crossover(Integer[] p1, Integer[] p2) {
        Integer[] child = new Integer[11];
        int cut = random.nextInt(10);
        for (int i = 0; i < 10; i++) child[i] = (i < cut) ? p1[i] : p2[i];
        child[10] = 0;
        return child;
    }

    private Integer[] mutateGenome(Integer[] genome) {
        Integer[] newGenome = Arrays.copyOf(genome, 11);
        int geneIdx = random.nextInt(10);
        if (random.nextBoolean()) {
            double factor = 0.8 + (random.nextDouble() * 0.4);
            newGenome[geneIdx] = (int) (newGenome[geneIdx] * factor);
        } else {
            newGenome[geneIdx] += (random.nextInt(5) - 2);
        }
        return newGenome;
    }
    
    private int mutate(int value) {
        double factor = 0.8 + (random.nextDouble() * 0.4);
        return (int) (value * factor);
    }
    
    private int playMatch(int[] w1, int[] w2, int depth) {
        AwariLong board = new AwariLong();
        int moves = 0;
        while (board.getNbSeeds() > 6 && moves < 200) {
            int cp = board.getCurrentPlayer();
            int[] cw = (cp == 0) ? w1 : w2;
            int bestMove = -1;
            double bestVal = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(cp, i)) {
                    AwariLong next = new AwariLong(board);
                    next.playMove(i);
                    double val = minimaxSim(next, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, cp, cw);
                    if (val > bestVal) { bestVal = val; bestMove = i; }
                }
            }
            if (bestMove == -1) break;
            board.playMove(bestMove);
            moves++;
        }
        int s1 = board.getScore(0);
        int s2 = board.getScore(1);
        int seeds = board.getNbSeeds();
        if (board.getCurrentPlayer() == 0) s2 += seeds; else s1 += seeds;
        return Integer.compare(s1, s2);
    }
    
    private double minimaxSim(AwariLong board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, int[] w) {
        if (depth == 0 || board.getNbSeeds() < 2) return evaluateSim(board, myPlayerIndex, w);
        int cp = board.getCurrentPlayer();
        boolean hasMove = false;
        for(int i=0; i<6; i++) if (board.isValidMove(cp, i)) { hasMove = true; break; }
        if (!hasMove) {
             int seeds = board.getNbSeeds();
             int sP = board.getScore(myPlayerIndex);
             int sO = board.getScore(1-myPlayerIndex);
             if (cp == myPlayerIndex) sO += seeds; else sP += seeds;
             return 10000.0 * (sP - sO);
        }
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(cp, i)) {
                    AwariLong next = new AwariLong(board);
                    next.playMove(i);
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
                if (board.isValidMove(cp, i)) {
                    AwariLong next = new AwariLong(board);
                    next.playMove(i);
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
    
    private double evaluateSim(AwariLong board, int playerIndex, int[] w) {
        int opponentIndex = 1 - playerIndex;
        int[] myHoles = new int[6];
        int[] oppHoles = new int[6];
        for(int i=0; i<6; i++) {
            myHoles[i] = board.getSeeds(playerIndex, i);
            oppHoles[i] = board.getSeeds(opponentIndex, i);
        }
        return evaluateSimRaw(myHoles, oppHoles, board.getScore(playerIndex), board.getScore(opponentIndex), w);
    }

    @Override
    public double[] getDecision(Board board) {
        double[] bestDecisions = new double[Board.NB_HOLES];
        Arrays.fill(bestDecisions, Double.NEGATIVE_INFINITY);
        long startTime = System.currentTimeMillis();
        timeOut = false;
        AwariLong rootState = AwariLong.fromBoard(board);
        int currentPlayer = rootState.getCurrentPlayer();
        for (int depth = 1; depth <= 60; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            boolean searchCompleted = true;
            for (int i = 0; i < 6; i++) {
                if (rootState.isValidMove(currentPlayer, i)) {
                    AwariLong nextState = new AwariLong(rootState);
                    nextState.playMove(i);
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

    private double minimax(AwariLong board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) { timeOut = true; return 0; }
        if (depth == 0 || board.getNbSeeds() < 2) return evaluateSim(board, myPlayerIndex, weights);
        int currentPlayer = board.getCurrentPlayer();
        boolean hasMove = false;
        for(int i=0; i<6; i++) if (board.isValidMove(currentPlayer, i)) { hasMove = true; break; }
        if (!hasMove) {
             int seeds = board.getNbSeeds();
             int scoreP = board.getScore(myPlayerIndex);
             int scoreO = board.getScore(1-myPlayerIndex);
             if (currentPlayer == myPlayerIndex) scoreO += seeds; else scoreP += seeds;
             return 10000.0 * (scoreP - scoreO);
        }
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(currentPlayer, i)) {
                    AwariLong nextState = new AwariLong(board);
                    nextState.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, false, myPlayerIndex, startTime);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                }
            }
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValidMove(currentPlayer, i)) {
                    AwariLong nextState = new AwariLong(board);
                    nextState.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, true, myPlayerIndex, startTime);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            return minEval;
        }
    }
}

*/