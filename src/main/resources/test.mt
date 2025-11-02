summit() {
    altitude a <> 3;
    altitude b, c;
    b <> 2;
    c <> 3;

    c <> a - b;
    climb (altitude i <> 0; i <> 5; i++) {
        c <> c + a;
        c <> c - b;
    }

    radio(c);
    descend;
}