routeplan basecamp summit() {
    altitude i;
    altitude j;

    altitude grid[3][3];

    climb (altitude i <> 0; i < 3; i <> i + 1) {
        climb (altitude j <> 0; j < 3; j <> j + 1) {
            grid[i][j] <> i * 10 + j;
        }
    }

    climb (altitude i <> 0; i < 3; i <> i + 1) {
        climb (altitude j <> 0; j < 3; j <> j + 1) {
            shout("Value:");
            radio(grid[i][j]);
        }
    }

    descend;
}
