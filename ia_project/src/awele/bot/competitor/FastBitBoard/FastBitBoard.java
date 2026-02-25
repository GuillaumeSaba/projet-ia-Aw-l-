package awele.bot.competitor.FastBitBoard;

import awele.core.Board;

public class FastBitBoard {
    // data[0] contient les 6 trous du J0, data[1] contient les 6 trous du J1 (6 bits par trou)
    long[] data = new long[2];
    int[] scores = new int[2];
    int currentPlayer;

    private FastBitBoard() {}

    // Conversion depuis le vrai Board
    public FastBitBoard(Board b) {
        this.currentPlayer = b.getCurrentPlayer();
        this.scores[0] = b.getScore(0);
        this.scores[1] = b.getScore(1);

        int pIdx = this.currentPlayer;
        int oIdx = 1 - pIdx;
        int[] pHoles = b.getPlayerHoles();
        int[] oHoles = b.getOpponentHoles();

        for(int i = 0; i < 6; i++) {
            this.data[pIdx] |= ((long) pHoles[i] << (i * 6));
            this.data[oIdx] |= ((long) oHoles[i] << (i * 6));
        }
    }

    // Accesseur ultra-rapide
    public int getSeeds(int player, int hole) {
        return (int) ((data[player] >> (hole * 6)) & 63);
    }

    // Le clonage instantané sans aucun 'new' lourd
    public FastBitBoard cloneBoard() {
        FastBitBoard next = new FastBitBoard();
        next.data[0] = this.data[0];
        next.data[1] = this.data[1];
        next.scores[0] = this.scores[0];
        next.scores[1] = this.scores[1];
        next.currentPlayer = this.currentPlayer;
        return next;
    }

    public FastBitBoard playMove(int holeIndex) {
        FastBitBoard next = this.cloneBoard();

        int side = next.currentPlayer;
        int seeds = next.getSeeds(side, holeIndex);

        // On vide le trou de départ (on efface ses 6 bits avec un masque inverse)
        next.data[side] &= ~(63L << (holeIndex * 6));

        int currentSide = side;
        int currentHole = holeIndex;

        // Égrenage ultra-rapide
        while (seeds > 0) {
            currentHole++;
            if (currentHole > 5) {
                currentSide = 1 - currentSide;
                currentHole = 0;
            }
            if (currentSide == side && currentHole == holeIndex) continue; // On saute le trou de départ

            // L'addition binaire magique (1 instruction CPU)
            next.data[currentSide] += (1L << (currentHole * 6));
            seeds--;
        }

        // Capture
        int oppSide = 1 - side;
        if (currentSide == oppSide) {
            int tempScore = 0;
            int ptr = currentHole;

            while (ptr >= 0) {
                int s = next.getSeeds(oppSide, ptr);
                if (s == 2 || s == 3) {
                    tempScore += s;
                    // On vide le trou capturé
                    next.data[oppSide] &= ~(63L << (ptr * 6));
                    ptr--;
                } else {
                    break;
                }
            }
            next.scores[side] += tempScore;
        }

        next.currentPlayer = 1 - next.currentPlayer;
        return next;
    }

    public boolean isValid(int holeIndex) {
        return getSeeds(this.currentPlayer, holeIndex) > 0;
    }
}