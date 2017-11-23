package controllers;

import play.mvc.Controller;
import play.mvc.Result;
import views.html.IndexTemplate;

import javax.inject.Inject;

public class Application extends Controller {
    private final views.html.IndexTemplate index;

    @Inject
    public Application(views.html.IndexTemplate index) {
      this.index = index;
    }

    public Result index() {
    return ok(index.render());
  }
}