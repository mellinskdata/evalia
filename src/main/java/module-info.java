module br.edu.evalia {
    requires javafx.controls;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires chesslib;

    exports br.edu.evalia;
    exports br.edu.evalia.model;
    exports br.edu.evalia.service;
}
