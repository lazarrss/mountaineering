routeplan basecamp summit() {
    altitude a <> -3;
    slope b <> -4.3
    mark ch <> 'C';
    route str <> "String example";
    secure boolean <> anchored;

    climb (altitude i <> 0; i <> 5; i++) {
        b <> b + 2.1;
        a <> a + 4;
    }

    radio(b);
    descend;
}