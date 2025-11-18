routeplan altitude addAltitudes(altitude a, altitude b) {
    altitude result <> a + b;
    descend result;
}

routeplan basecamp printResult(altitude value) {
    shout("Result:");
    radio(value);
    descend;
}

routeplan basecamp summit() {
    altitude x <> 10;
    altitude y <> 20;
    altitude z;

    z <> 3;
    z <> addAltitudes(x, y);
    printResult(z);

    descend;
}
