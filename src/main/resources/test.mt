summit() {
    altitude a <> 3;
    altitude b, c;
    b <> 2;
    c <> 3;
    slope d <> 2.3;
    slope e, f;
    e <> 2.1
    f <> 5.0

    c <> a - b;
    f <> d - e
    climb (altitude i <> 0; i <> 5; i++) {
        c <> c + a;
        c <> c - b;

        f <> f + d;
        f <> f - e
    }

    radio(c);
    radio(f)
    descend;
}