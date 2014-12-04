var santa = angular.module("santa", ["restangular", "ngRoute"]);

santa.factory("User", function() {
	return {
		logged: false,
		setLogin : function(){this.logged = true}
	}
});

santa.config(['$routeProvider', function($routeProvider){
	$routeProvider
		.when("/", 
			{
				templateUrl: "/login.html",
				controller: "Login"})
		.when("/draw", 
			{
				templateUrl: "/draw.html",
				controller: "Draw"
			})
		.otherwise({
			redirectTo: "/"
		});
	}
]);

santa.controller("Login", ["$scope", "Restangular", "$location", "User", function($scope, Restangular, $location, User){
	$scope.email = "";
 	$scope.login = function(){
 		Restangular.all("login").post({username: $scope.email}).then(function(data){
 			if (data.success){
 				$location.path("/draw");
 				User.setLogin();
 				console.log("user.logged is " + User.logged)
 			}});
 	};

}]);

santa.controller("Draw", ["$scope", function($scope){

}]);