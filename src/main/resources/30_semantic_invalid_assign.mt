routeplan slope f() {
    descend 7.0;
}

routeplan basecamp summit() {
    altitude i;
    slope s <> 7.0;

    altitude grid[2][2];

    altitude a <> (altitude) (+(5) + (3 * 2));

    altitude x <> (altitude) s;

    altitude y <> (altitude) f();

    grid[0][1] <> a + x;

    ifridge (a < 10) {
        shout("OK");
    } elsecamp {
        shout("NO");
    }

    descend;
}
