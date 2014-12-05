var santa = angular.module("santa", ["restangular", "ngRoute"]);

santa.factory("User", function() {
	return {
		logged: false,
		id: ""
	
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
 				User.logged = true;
 				User.id = data.id;
 				console.log("user is " + User);
 			}});
 	};

}]);

santa.controller("Draw", ["$scope", "User", "Restangular", function($scope, User, Restangular){
	$scope.isDrawError = false;
	$scope.drawError = "";

	$scope.draw = function(){
		console.log("The user ID is " + User.id);
		Restangular.one("draw", User.id)
		.get()
		.then(function(resp){
			console.log("Returning from draw REST call");
			console.log(resp);
		},
		function(data){
			console.log("Error");
			console.log(data);
			$scope.drawError = data.statusText;
			$scope.isDrawError = true;
		});
	};
}]);