package dev.piolunson.v2.util.network;

@FunctionalInterface
public interface NetworkAction {
   void run(NetworkActionContext var1) throws Exception;
}
