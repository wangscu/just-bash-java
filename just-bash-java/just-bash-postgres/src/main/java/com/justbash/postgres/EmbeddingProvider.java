package com.justbash.postgres;

import java.util.concurrent.CompletableFuture;

public interface EmbeddingProvider {
    CompletableFuture<float[]> embed(String text);
}
