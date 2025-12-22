routeplan basecamp summit() {
    altitude i;
    altitude j;

    altitude grid[3][3];
    altitude a[2], x <> 5, b[10];

    climb (i <> 0; i < 3; i <> i + 1) {
        climb (j <> 0; j < 3; j <> j + 1) {
            grid[i][j] <> i * 10 + j;
        }
    }

    climb (i <> 0; i < 3; i <> i + 1) {
        climb (j <> 0; j < 3; j <> j + 1) {
            shout("Value:");
            shout(grid[i][j]);
        }
    }

    descend;
}
