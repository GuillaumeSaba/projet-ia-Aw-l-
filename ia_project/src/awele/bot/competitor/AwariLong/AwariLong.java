package awele.bot.competitor.AwariLong;

import awele.core.Board;

public class AwariLong {
    private long[] data;

    public AwariLong() {
        data = new long[2];
        for (int i = 0; i < 6; i++) {
            setSeeds(0, i, 4);
            setSeeds(1, i, 4);
        }
        updateValidMoves();
    }

    public AwariLong(AwariLong other) {
        this.data = new long[2];
        this.data[0] = other.data[0];
        this.data[1] = other.data[1];
    }

    public int getCurrentPlayer() {
        return (int) ((data[0] >> 42) & 1);
    }

    public int getScore(int player) {
        return (int) ((data[player] >> 36) & 63);
    }

    public int getSeeds(int player, int hole) {
        return (int) ((data[player] >> (hole * 6)) & 63);
    }

    public boolean isValidMove(int player, int hole) {
        return ((data[player] >> (43 + hole)) & 1) == 1;
    }

    public int getNbSeeds() {
        int seeds = 0;
        for (int p = 0; p < 2; p++) {
            for (int h = 0; h < 6; h++) {
                seeds += getSeeds(p, h);
            }
        }
        return seeds;
    }

    private void setSeeds(int player, int hole, int seeds) {
        data[player] &= ~(63L << (hole * 6));
        data[player] |= ((long) seeds << (hole * 6));
    }

    private void addScore(int player, int points) {
        int currentScore = getScore(player);
        int newScore = currentScore + points;
        data[player] &= ~(63L << 36);
        data[player] |= ((long) newScore << 36);
    }

    private void switchPlayer() {
        data[0] ^= (1L << 42);
    }

    public void playMove(int hole) {
        int player = getCurrentPlayer();
        int opponent = 1 - player;
        int seeds = getSeeds(player, hole);
        setSeeds(player, hole, 0);

        int currentSide = player;
        int currentHole = hole;

        // Sowing
        while (seeds > 0) {
            currentHole++;
            if (currentHole >= 6) {
                currentSide = 1 - currentSide;
                currentHole = 0;
            }
            if (currentSide != player || currentHole != hole) {
                setSeeds(currentSide, currentHole, getSeeds(currentSide, currentHole) + 1);
                seeds--;
            }
        }

        // Capture logic
        if (currentSide == opponent) {
            // Check for "take all" rule before capturing
            int opponentTotalSeeds = 0;
            for (int i = 0; i < 6; i++) {
                opponentTotalSeeds += getSeeds(opponent, i);
            }

            int seedsToCapture = 0;
            int tempCaptureHole = currentHole;
            while (tempCaptureHole >= 0) {
                int seedsInHole = getSeeds(opponent, tempCaptureHole);
                if (seedsInHole == 2 || seedsInHole == 3) {
                    seedsToCapture += seedsInHole;
                    tempCaptureHole--;
                } else {
                    break;
                }
            }

            // If the capture is valid (i.e., not a "take all" move), then proceed
            if (seedsToCapture > 0 && seedsToCapture < opponentTotalSeeds) {
                int captureHole = currentHole;
                while (captureHole >= 0) {
                    int seedsInHole = getSeeds(opponent, captureHole);
                    if (seedsInHole == 2 || seedsInHole == 3) {
                        addScore(player, seedsInHole);
                        setSeeds(opponent, captureHole, 0);
                        captureHole--;
                    } else {
                        break;
                    }
                }
            }
        }

        switchPlayer();
        updateValidMoves();
    }

    private void updateValidMoves() {
        int player = getCurrentPlayer();
        int opponent = 1 - player;
        
        boolean opponentEmpty = true;
        for (int i = 0; i < 6; i++) {
            if (getSeeds(opponent, i) > 0) {
                opponentEmpty = false;
                break;
            }
        }

        int nbMoves = 0;
        long validMask = 0;

        for (int i = 0; i < 6; i++) {
            int seeds = getSeeds(player, i);
            boolean valid = false;
            
            if (seeds > 0) {
                if (!opponentEmpty) {
                    valid = true;
                } else {
                    if (seeds > (5 - i)) { // Must feed the opponent
                        valid = true;
                    }
                }
            }
            
            if (valid) {
                validMask |= (1L << (43 + i));
                nbMoves++;
            }
        }

        data[player] &= ~(511L << 43); 
        data[player] |= validMask;
        data[player] |= ((long) nbMoves << 49);
    }
    
    public static AwariLong fromBoard(Board board) {
        AwariLong al = new AwariLong();
        al.data[0] = 0;
        al.data[1] = 0;
        
        int cp = board.getCurrentPlayer();
        int[] holes0, holes1;
        
        if (cp == 0) {
            holes0 = board.getPlayerHoles();
            holes1 = board.getOpponentHoles();
        } else {
            holes1 = board.getPlayerHoles();
            holes0 = board.getOpponentHoles();
        }
        
        for(int i=0; i<6; i++) {
            al.setSeeds(0, i, holes0[i]);
            al.setSeeds(1, i, holes1[i]);
        }
        
        al.addScore(0, board.getScore(0));
        al.addScore(1, board.getScore(1));
        
        if (cp == 1) {
            al.data[0] |= (1L << 42);
        }
        
        al.updateValidMoves();
        return al;
    }
}