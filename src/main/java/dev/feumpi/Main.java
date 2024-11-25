package dev.feumpi;

import io.javalin.Javalin;

public class Main {
    public static void main(String[] args) {
        var app = Javalin.create(/*config*/)
                .get("/", ctx -> ctx.result("Hello World"))
                .get("/remediario", ctx -> ctx.result("Remediario"))
                .start(7070);
    }
}