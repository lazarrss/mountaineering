routeplan altitude add(altitude x, altitude y) {
    altitude result <> x + y;
    descend result;
}

routeplan slope half(slope s) {
    slope r <> s / 2;
    descend r;
}

routeplan basecamp printAll(altitude a, slope b) {
    shout("Altitude:");
    radio(a);
    shout("Slope:");
    radio(b);
    descend;
}

routeplan basecamp summit() {
    altitude x <> 7;
    altitude y <> 13;
    slope s <> 10.0;

    altitude sum <> add(x, y);
    slope halfVal <> half(s);

    printAll(sum, halfVal);

    route r <> whiteout;

    shout("Finished");
    descend;
}
