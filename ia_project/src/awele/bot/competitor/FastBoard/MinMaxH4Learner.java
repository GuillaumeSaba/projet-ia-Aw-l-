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

    private static final long TIME_LIMIT_MS = 95; // On profite des 100ms
    private boolean timeOut = false;
    private long nodes = 0;

    // Poids par défaut (Ceux de votre FastBoard classique qui gagne)
    // [0]=FacteurScore, [1]=BonusKrou, [2]=MalusVide, [3]=MalusVulnérable, etc...
    private int[] defaultWeights = {25, 28, -50, -36, 0, 0, 0, 0, 0, 0};
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
        // Par défaut, on utilise les poids de base
        System.arraycopy(defaultWeights, 0, weights, 0, 10);
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

        // --- PHASE 0 : BENCHMARK RAPIDE ---
        System.err.println("Starting Benchmark...");
        int matches = 0;
        long benchStart = System.currentTimeMillis();
        while (System.currentTimeMillis() - benchStart < 1000) {
            playMatch(defaultWeights, defaultWeights, 4);
            matches++;
        }
        System.err.println("Benchmark Result: " + matches + " matches/sec (depth 4)");

        // Ajustement dynamique selon la puissance du PC
        int popSize = 20;
        int simDepth = 6;
        if (matches > 5000) { popSize = 40; simDepth = 8; }
        else if (matches > 2000) { popSize = 30; simDepth = 7; }

        // --- PHASE 1 : APPRENTISSAGE ---
        // On laisse 1 minute de marge à la fin pour la finale
        long maxDuration = 3600000 - 60000;

        // Chargement du dataset (Attention au chemin, assurez-vous qu'il est bon)
        loadDataset("C:/Users/Guillaume/projet-ia-Aw-l-/ia_project/data/awele.data");

        // Initialisation de la population
        Integer[][] population = new Integer[popSize][11]; // [10] = Score Fitness

        // L'individu 0 est notre champion actuel (pour ne pas régresser)
        for(int i=0; i<10; i++) population[0][i] = defaultWeights[i];
        population[0][10] = 0;

        // Les autres sont aléatoires ou mutés
        for (int i = 1; i < popSize; i++) {
            population[i] = new Integer[11];
            for (int w = 0; w < 10; w++) {
                if (w < 4) population[i][w] = mutate(population[0][w]); // Poids importants mutés
                else population[i][w] = random.nextInt(21) - 10; // Poids secondaires aléatoires
            }
            population[i][10] = 0;
        }

        int generation = 0;
        while (System.currentTimeMillis() - startTime < maxDuration) {

            // ÉVALUATION DE LA POPULATION
            for (int i = 0; i < popSize; i++) {
                int[] w = toIntArray(population[i]);

                // 1. Score sur le Dataset (On divise l'importance par 10)
                // Le dataset ne sert qu'à donner une "intuition"
                int scoreData = evaluateOnDataset(w);

                // 2. Score en Match Réel (Self-Play) -> C'est ça qui compte !
                int scorePlay = 0;
                // On joue contre 3 adversaires aléatoires de la population
                for (int j = 0; j < 3; j++) {
                    int opponentIdx = random.nextInt(popSize);
                    if (i == opponentIdx) continue;

                    // On joue à une profondeur fixe pour aller vite
                    int result = playMatch(w, toIntArray(population[opponentIdx]), 4);

                    if (result > 0) scorePlay += 50; // VICTOIRE = 50 points !
                    else if (result == 0) scorePlay += 10; // EGALITE = 10 points
                }

                // Le score total privilégie massivement la victoire réelle
                population[i][10] = scoreData + scorePlay;
            }

            // TRI : Les meilleurs en haut
            Arrays.sort(population, Comparator.comparingInt(a -> -a[10]));



            // REPRODUCTION (Élitisme + Croisement)
            Integer[][] newPopulation = new Integer[popSize][11];

            // On garde les 3 meilleurs intacts (Élitisme)
            for(int k=0; k<3; k++) newPopulation[k] = Arrays.copyOf(population[k], 11);

            // Les autres sont des enfants
            for (int i = 3; i < popSize; i++) {
                Integer[] parent1 = population[random.nextInt(popSize / 2)]; // Sélection dans la meilleure moitié
                Integer[] parent2 = population[random.nextInt(popSize / 2)];
                Integer[] child = crossover(parent1, parent2);

                // Mutation (20% de chance)
                if (random.nextDouble() < 0.20) child = mutateGenome(child);

                newPopulation[i] = child;
            }
            population = newPopulation;
            generation++;
        }

        // --- PHASE 2 : LA GRANDE FINALE ---
        // Le meilleur poids appris affronte les poids par défaut sur un match sérieux (profondeur élevée)
        int[] bestLearned = toIntArray(population[0]);
        System.err.println("Learning finished. Best candidate: " + Arrays.toString(bestLearned));

        System.err.println("FINAL MATCH: Learned vs Default (Depth " + simDepth + ")...");
        int scoreLearned = 0;

        // Match Aller (Appris commence)
        int res1 = playMatch(bestLearned, defaultWeights, simDepth);
        if (res1 > 0) scoreLearned += 3;
        else if (res1 == 0) scoreLearned += 1;

        // Match Retour (Défaut commence)
        int res2 = playMatch(defaultWeights, bestLearned, simDepth);
        if (res2 < 0) scoreLearned += 3; // Si défaut perd, c'est que appris (J2) a gagné
        else if (res2 == 0) scoreLearned += 1;

        if (scoreLearned >= 4) {
            System.err.println("SUCCESS: Learned weights are BETTER! Updating bot.");
            // On met à jour les poids définitifs du bot
            System.arraycopy(bestLearned, 0, weights, 0, 10);
        } else {
            System.err.println("FAILURE: Default weights are still superior. Discarding learned weights.");
            // On garde les poids par défaut (déjà dans 'weights')
        }
    }

    private void loadDataset(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine(); // Header
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
        } catch (IOException e) {
            System.err.println("Warning: Dataset not found at " + path);
        }
    }

    // Évalue si les poids devinent le bon résultat (Gagnant/Perdant)
    private int evaluateOnDataset(int[] w) {
        int score = 0;
        for (TrainingData data : dataset) {
            double eval = evaluateSimRaw(data.myHoles, data.oppHoles, 0, 0, w);
            // Si la donnée dit "Gagné" et notre eval est positive -> Point
            if (data.winning && eval > 0) score += 1;
                // Si la donnée dit "Perdu" et notre eval est négative -> Point
            else if (!data.winning && eval < 0) score += 1;
        }
        return score;
    }

    // Helpers génétiques
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
            double factor = 0.8 + (random.nextDouble() * 0.4); // Variation +/- 20%
            newGenome[geneIdx] = (int) (newGenome[geneIdx] * factor);
        } else {
            newGenome[geneIdx] += (random.nextInt(5) - 2); // Variation absolue petite
        }
        return newGenome;
    }

    private int mutate(int value) {
        double factor = 0.8 + (random.nextDouble() * 0.4);
        return (int) (value * factor);
    }

    // --- MOTEUR DE JEU (SELF PLAY) ---
    private int playMatch(int[] w1, int[] w2, int depth) {
        FastBoard board = new FastBoard(); // Plateau vide initial (0-0 partout c'est pas valide mais ici c'est pour l'objet)
        // Correction : On doit initialiser un vrai plateau de départ classique
        // Pour simplifier, on simule un plateau de départ 4 graines partout
        for(int i=0; i<12; i++) board.holes[i] = 4;
        board.scores[0]=0; board.scores[1]=0; board.currentPlayer=0;

        int moves = 0;
        // Condition d'arrêt : moins de 6 graines ou trop de coups
        while (board.getTotalSeeds() > 6 && moves < 150) {
            int cp = board.currentPlayer;
            int[] cw = (cp == 0) ? w1 : w2;

            // Mini-MinMax pour le coup
            int bestMove = -1;
            double bestVal = Double.NEGATIVE_INFINITY;

            // On doit trier les coups un minimum pour la vitesse
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    FastBoard next = board.playMove(i);
                    // Appel récursif
                    double val = minimaxSim(next, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false, cp, cw);
                    if (val > bestVal) { bestVal = val; bestMove = i; }
                }
            }

            if (bestMove == -1) break; // Plus de coup possible (Famine)
            board = board.playMove(bestMove);
            moves++;
        }

        // Calcul du score final avec les graines restantes
        int s1 = board.scores[0];
        int s2 = board.scores[1];
        int remaining = board.getTotalSeeds();
        // Règle simplifiée : le joueur courant ne capture pas le reste s'il est bloqué, mais ici c'est une simu rapide
        if (board.currentPlayer == 0) s2 += remaining; else s1 += remaining;

        return Integer.compare(s1, s2);
    }

    // Minimax simplifié pour l'apprentissage (pas de gestion de temps complexe, juste de la profondeur)
    private double minimaxSim(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, int[] w) {
        if (depth == 0 || board.getTotalSeeds() < 6) return evaluateSim(board, myPlayerIndex, w);

        boolean hasMove = false;
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard next = board.playMove(i); // Utilise le constructeur de copie !
                    double eval = minimaxSim(next, depth - 1, alpha, beta, false, myPlayerIndex, w);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                }
            }
            if (!hasMove) return evaluateSim(board, myPlayerIndex, w);
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard next = board.playMove(i);
                    double eval = minimaxSim(next, depth - 1, alpha, beta, true, myPlayerIndex, w);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                }
            }
            if (!hasMove) return evaluateSim(board, myPlayerIndex, w);
            return minEval;
        }
    }

    // --- FONCTIONS D'ÉVALUATION ---
    private double evaluateSimRaw(int[] myHoles, int[] oppHoles, int myScore, int oppScore, int[] w) {
        // w[0] = Facteur de Score
        double score = w[0] * (myScore - oppScore);

        int mySeeds = 0, oppSeeds = 0;
        int myMobility = 0, oppMobility = 0;

        for (int i = 0; i < 6; i++) {
            int s = myHoles[i];
            int os = oppHoles[i];
            mySeeds += s;
            oppSeeds += os;

            if (s > 0) myMobility++;
            if (os > 0) oppMobility++;

            // w[1]=Bonus Krou, w[2]=Malus Vide, w[3]=Malus Vulnérable
            if (s > 12) score += w[1];
            else if (s == 0) score += w[2];
            else if (s < 3) score += w[3];

            if (os > 12) score -= w[1];
            else if (os == 0) score -= w[2];
            else if (os < 3) score -= w[3];
        }

        // Autres heuristiques expérimentales
        score += w[4] * (mySeeds - oppSeeds);
        score += w[5] * (myMobility - oppMobility);

        return score;
    }

    private double evaluateSim(FastBoard board, int playerIndex, int[] w) {
        int opponentIndex = 1 - playerIndex;
        // On reconstruit les tableaux pour evaluateSimRaw
        int[] myHoles = new int[6];
        int[] oppHoles = new int[6];
        for(int i=0; i<6; i++) {
            myHoles[i] = board.holes[playerIndex * 6 + i];
            oppHoles[i] = board.holes[opponentIndex * 6 + i];
        }
        return evaluateSimRaw(myHoles, oppHoles, board.scores[playerIndex], board.scores[opponentIndex], w);
    }

    // --- LOGIQUE DU BOT EN COMPÉTITION (OPTIMISÉE) ---
    @Override
    public double[] getDecision(Board board) {
        int currentPlayer = board.getCurrentPlayer();

        // 1. Ouverture Forcée (Uniquement si valide)
        if (board.getLog(currentPlayer).length == 0 && board.getLog(Board.otherPlayer(currentPlayer)).length == 0) {
            if (board.validMoves(currentPlayer)[5]) {
                double[] opening = new double[6];
                Arrays.fill(opening, Double.NEGATIVE_INFINITY);
                opening[5] = 1.0;
                return opening;
            }
        }

        double[] bestDecisions = new double[Board.NB_HOLES];
        Arrays.fill(bestDecisions, Double.NEGATIVE_INFINITY);

        long startTime = System.currentTimeMillis();
        this.timeOut = false;
        this.nodes = 0; // RESET CRITIQUE

        FastBoard rootState = new FastBoard(board);

        // Iterative Deepening
        for (int depth = 1; depth <= 60; depth++) {
            double[] currentDecisions = new double[Board.NB_HOLES];
            boolean searchCompleted = true;

            // Tri des coups basé sur l'itération précédente
            Integer[] moveOrder = {0, 1, 2, 3, 4, 5};
            if (depth > 1) {
                final double[] prev = bestDecisions;
                Arrays.sort(moveOrder, (a, b) -> Double.compare(prev[b], prev[a]));
            }

            for (int i : moveOrder) {
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
                System.arraycopy(currentDecisions, 0, bestDecisions, 0, 6);
            } else {
                break;
            }
        }
        return bestDecisions;
    }

    private double minimax(FastBoard board, int depth, double alpha, double beta, boolean maximizingPlayer, int myPlayerIndex, long startTime) {
        // Vérification Temps tous les 512 noeuds
        if ((nodes++ & 511) == 0) {
            if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                timeOut = true;
                return 0.0;
            }
        }

        if (depth == 0 || board.getTotalSeeds() < 6) {
            return evaluateSim(board, myPlayerIndex, weights); // Utilise les poids appris !
        }

        boolean hasMove = false;
        if (maximizingPlayer) {
            double maxEval = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard nextState = board.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, false, myPlayerIndex, startTime);
                    maxEval = Math.max(maxEval, eval);
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha) break;
                    if (timeOut) return maxEval;
                }
            }
            if (!hasMove) return evaluateSim(board, myPlayerIndex, weights);
            return maxEval;
        } else {
            double minEval = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 6; i++) {
                if (board.isValid(i)) {
                    hasMove = true;
                    FastBoard nextState = board.playMove(i);
                    double eval = minimax(nextState, depth - 1, alpha, beta, true, myPlayerIndex, startTime);
                    minEval = Math.min(minEval, eval);
                    beta = Math.min(beta, eval);
                    if (beta <= alpha) break;
                    if (timeOut) return minEval;
                }
            }
            if (!hasMove) return evaluateSim(board, myPlayerIndex, weights);
            return minEval;
        }
    }

    // =========================================================================
    // FASTBOARD OPTIMISÉ (AVEC CONSTRUCTEUR DE COPIE)
    // =========================================================================
    private class FastBoard {
        int[] holes = new int[12];
        int[] scores = new int[2];
        int currentPlayer;

        // Constructeur privé pour initialisation manuelle (utilisé dans playMatch)
        private FastBoard() {}

        // Constructeur officiel
        public FastBoard(Board b) {
            this.currentPlayer = b.getCurrentPlayer();
            this.scores[0] = b.getScore(0);
            this.scores[1] = b.getScore(1);
            int[] p0 = (this.currentPlayer == 0) ? b.getPlayerHoles() : b.getOpponentHoles();
            int[] p1 = (this.currentPlayer == 1) ? b.getPlayerHoles() : b.getOpponentHoles();
            for (int i = 0; i < 6; i++) {
                this.holes[i] = p0[i];
                this.holes[6 + i] = p1[i];
            }
        }

        // Constructeur de COPIE (CRUCIAL POUR LA VITESSE)
        private FastBoard(FastBoard source) {
            System.arraycopy(source.holes, 0, this.holes, 0, 12);
            this.scores[0] = source.scores[0];
            this.scores[1] = source.scores[1];
            this.currentPlayer = source.currentPlayer;
        }

        public int getTotalSeeds() { return 48 - this.scores[0] - this.scores[1]; }

        public boolean isValid(int moveIndex) {
            int pos = this.currentPlayer * 6 + moveIndex;
            if (this.holes[pos] == 0) return false;
            int oppSide = 1 - this.currentPlayer;
            boolean opponentEmpty = true;
            for (int i = 0; i < 6; i++) { if (this.holes[oppSide * 6 + i] > 0) { opponentEmpty = false; break; } }
            if (opponentEmpty) return (moveIndex + this.holes[pos] >= 6);
            return true;
        }

        public FastBoard playMove(int moveIndex) {
            // Utilisation du constructeur de copie -> Pas de re-allocations lourdes
            FastBoard next = new FastBoard(this);

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