package socs.network.node;

public class Link {

  RouterDescription router1;
  RouterDescription router2;
  private int weight;

  public Link(RouterDescription r1, RouterDescription r2, int weight) {
    router1 = r1;
    router2 = r2;
    this.weight = weight;
  }

  public void addWeight(int weight) {

  }

  public int getWeight() {
    return weight;
  }
}
