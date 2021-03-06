import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;
import static spark.Spark.*;

public class App {
  private static final int[] board = {0,1,2,3,4,5,6,7};

  public static void main(String[] args) {
    staticFileLocation("/public");
    String layout = "templates/layout.vtl";

    get("/", (request, response) -> {
      Map<String, Object> model = new HashMap<>();
      model.put("loggedInStatus", User.loggedIn);
      model.put("loggedInUser", User.loggedInUser);
      model.put("template", "templates/index.vtl");
      return new ModelAndView(model, layout);
    }, new VelocityTemplateEngine());

    post("/game/new", (request, response) -> {
      int rawInput = Integer.parseInt(request.queryParams("players"));
      int players = rawInput/10;
      int aiType = rawInput%10;
      Game.deleteUnsaved();
      Game newGame = new Game(players,aiType);
      return new ModelAndView(boardModel(newGame), layout);
    }, new VelocityTemplateEngine());

    post("/checkers/:userid/:gameid/save", (request, response) -> {
      Map<String, Object> model = new HashMap<>();
      User user = User.find(Integer.parseInt(request.params("userid")));
      Game game = Game.findById(Integer.parseInt(request.params("gameid")));
      game.attachUser(user.getId());
      Game.deleteUnsaved();
      response.redirect("/user-page");
      return new ModelAndView(model, layout);
    }, new VelocityTemplateEngine());

    post("/moves/legal", (request, response) -> {
      List<Integer> legalRows = new ArrayList<Integer>();
      List<Integer> legalColumns = new ArrayList<Integer>();
      List<Integer> indexes = new ArrayList<Integer>();
      Checker checker = Checker.find(Integer.parseInt(request.queryParams("checker")));
      Game game = Game.findById(checker.getGameId());
      Map<String, Object> model = boardModel(game);
      for (int i = 0; i < board.length ; i++ ) {
        for (int j = 0; j < board.length ; j++ ) {
          if(game.specificMoveIsValid(checker, i, j) && checker.getType() % 2 == game.getPlayerTurn() % 2 || game.specificCaptureIsValid(checker, i, j)) {
            legalRows.add(i);
            legalColumns.add(j);
            indexes.add(legalRows.size()-1);
          }
        }
      }
      model.put("legalIndexes", indexes);
      model.put("legalRows", legalRows);
      model.put("legalColumns", legalColumns);
      model.put("currentChecker", checker);
      return new ModelAndView(model, layout);
    }, new VelocityTemplateEngine());

    post("/checker/:id/move", (request, response) -> {
      Checker checker = Checker.find(Integer.parseInt(request.params("id")));
      Game game = Game.findById(checker.getGameId());
      int position = Integer.parseInt(request.queryParams("move"));
      int column = position % 10;
      int row = position / 10;
      game.movePiece(checker, row, column);
      int doubleJumpAvailable = game.capturePiece(checker, row, column);
      Map<String, Object> model = boardModel(game);
      if (doubleJumpAvailable == 2 && game.getPlayerCount() == 2) {
        model.put("currentChecker", checker);
      } else if(game.getPlayerCount() == 1 && game.getAiType() == 1){
        EasyAI ai = new EasyAI(game.getId());
        ai.move();
        model = boardModel(ai.getCurrentGame());
      } else if(game.getPlayerCount() == 1 && game.getAiType() == 2){
        AI ai = new AI(game.getId());
        ai.move();
        model = boardModel(ai.getCurrentGame());
      }
      return new ModelAndView(model, layout);
    }, new VelocityTemplateEngine());

    get("/about", (request, response) -> {
      Map<String,Object> model = new HashMap<>();
      model.put("template", "templates/about.vtl");
      return new ModelAndView(model, layout);
    }, new VelocityTemplateEngine());

    get("/login",(request,response) -> { // Directs to sign in page
      Map<String,Object> model = new HashMap<>();
      model.put("template", "templates/login.vtl");
      return new ModelAndView(model, layout);
    },new VelocityTemplateEngine());

    post("/login", (request,response) -> { // Directs to home page if successful login
      Map<String,Object> model = new HashMap<>();
      String userName = request.queryParams("user-name");
      String password = request.queryParams("password");
      try {
        User newUser = User.login(userName,password);
        User.loggedIn = true;
        User.loggedInUser = newUser;
        model.put("games", User.loggedInUser.getGames());
        model.put("loggedInStatus", User.loggedIn);
        model.put("loggedInUser", User.loggedInUser);
        model.put("template", "templates/user-page.vtl");
        return new ModelAndView(model, layout);
      } catch(RuntimeException exception) {
        model.put("invalidLogin", true);
        model.put("template", "templates/login.vtl");
        return new ModelAndView(model, layout);
      }
    },new VelocityTemplateEngine());

    get("/user-page",(request,response) -> { // Directs to user page
      Map<String,Object> model = new HashMap<>();
      model.put("games", User.loggedInUser.getGames());
      model.put("loggedInStatus", User.loggedIn);
      model.put("loggedInUser", User.loggedInUser);
      model.put("template", "templates/user-page.vtl");
      return new ModelAndView(model, layout);
    },new VelocityTemplateEngine());

    get("/user/:userid/game/:id", (request, response) -> {
      int userId = Integer.parseInt(request.params("userid"));
      int gameId = Integer.parseInt(request.params("id"));
      Game game = Game.findById(gameId);
      return new ModelAndView(boardModel(game), layout);
    }, new VelocityTemplateEngine());

    post("/new-account", (request,response) -> {
      Map<String,Object> model = new HashMap<>();
      String userName = request.queryParams("create-user-name");
      String password = request.queryParams("create-password");
      if (!User.userAlreadyExists(userName)) { // If user name is valid, directs to home page
        User.loggedIn = true;
        User newUser = new User(userName, password);
        newUser.save();
        User.loggedInUser = newUser;
        model.put("loggedInStatus", User.loggedIn);
        model.put("loggedInUser", User.loggedInUser);
        model.put("template", "templates/index.vtl");
        return new ModelAndView(model, layout);
      } else {
        model.put("invalidUserName", true);
        return new ModelAndView(model, "templates/login.vtl");
      }
    },new VelocityTemplateEngine());

    post("/logout", (request, response) -> {
      Map<String,Object> model = new HashMap<>();
      User.loggedIn = false;
      User.loggedInUser = null;
      model.put("loggedInStatus", User.loggedIn);
      model.put("loggedInUser", User.loggedInUser);
      model.put("template", "templates/index.vtl");
      return new ModelAndView(model, layout);
    },new VelocityTemplateEngine());

  }

  public static Map<String,Object> boardModel(Game pGame) {
    Map<String,Object> model = new HashMap<>();
    List<Integer> takenRedCheckersList = new ArrayList<>();
    List<Integer> takenWhiteCheckersList = new ArrayList<>();
    for (int i = 0; i < pGame.getTakenRedCheckers(); i++) {
      takenRedCheckersList.add(i);
    }
    for (int j = 0; j < pGame.getTakenWhiteCheckers(); j++) {
      takenWhiteCheckersList.add(j);
    }
    model.put("playerTurn", pGame.getPlayerTurn());
    model.put("checkers", pGame.getCheckers());
    model.put("game", pGame);
    model.put("rows", board);
    model.put("columns", board);
    model.put("redsTaken", takenRedCheckersList);
    model.put("whitesTaken", takenWhiteCheckersList);
    model.put("gameOver", pGame.gameIsOver());
    model.put("loggedInStatus", User.loggedIn);
    model.put("loggedInUser", User.loggedInUser);
    model.put("template", "templates/checkers.vtl");
    return model;
  }
}
