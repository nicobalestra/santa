var santa = angular.module("santa", ["restangular", "ngRoute"]);

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

santa.controller("Login", ["$scope", "Restangular", "$location", function($scope, Restangular, $location){
	$scope.email = "";
 	$scope.login = function(){
 		Restangular.all("login").post({username: $scope.email}).then(function(data){
 			console.log("Post went ok..");
 			console.log("Logged in?: " + data.success);
 			if (data.success)
 				$location.path("/draw");
 		});
 	};

}]);

santa.controller("Draw", ["$scope", function($scope){

}]);