package br.edu.evalia.integration;

import java.io.IOException;
import java.net.URI;

public interface HttpGateway {
    HttpResult get(URI uri, String accept) throws IOException, InterruptedException;
}
