var santa = angular.module("santa", ["restangular", "ngRoute", "ngSanitize"]);

santa.factory("User", function() {
	return {
		logged: false,
		id: "",
		match: {}
	
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
		.when("/match",
			{
				templateUrl : "/match.html",
				controller: "Match"
			})
		.otherwise({
			redirectTo: "/"
		});
	}
]);

santa.controller("Login", ["$scope", "Restangular", "$location", "User", function($scope, Restangular, $location, User){
	$scope.email = "";
	$scope.loginFailed = false;
	$scope.password = "";
	$scope.codeSendingError = false;
	$scope.loginErrorMessage = ""
	$scope.loginNotFound = false;
	$scope.codeSent = false;

	function clearErrors(){
		$scope.loginFailed = false;
		$scope.codeSendingError = false;
		$scope.loginNotFound = false;
		$scope.codeSent = false;
	}

 	$scope.login = function(){
 		clearErrors();

 		if (!$scope.email || $scope.email == "")
 		{
 			$scope.loginFailed = true;
 			$scope.loginErrorMessage = "Please enter an email address";
 			return;
 		}

 		if (!$scope.password || $scope.password == ""){
 			$scope.loginFailed = true;
 			$scope.loginErrorMessage = "Please enter the password I've sent you via email when you pressed on the 'Send me the password button'";
 			return;

 		}

 		Restangular.all("login")
 					.post({username: $scope.email, password: $scope.password})
 					.then(
 						function(data){
 							if (data.success){
 								$location.path("/draw");
 								User.logged = true;
 								User.id = data.id;
 							}
 							else{
 								$scope.loginNotFound = true;
 							}},
 						function(data){
 							console.log(data);
 							$scope.httpError = true;

 						});
 	};

 	$scope.sendCode = function(){
 		clearErrors();

 		if (!$scope.email || $scope.email == ""){
 			$scope.codeSendingError = true;
 			$scope.codeErrorMessage = "Please enter an email address";
 			return;
 		}

 		Restangular.all("getcode")
 				   .post({username: $scope.email})
 				   .then(
 				   		function(data){
 				   			console.log(data);
 				   			if (!data.success){
 				   				$scope.codeSendingError = true;
 				   				$scope.codeErrorMessage = data.message;
 				   			}else{
 				   				$scope.codeSent = true;
 				   			}
 				   		})	
 	}

}]);

santa.controller("Draw", ["$scope", "User", "Restangular", "$location", function($scope, User, Restangular, $location){
	$scope.isDrawError = false;
	$scope.drawError = "";
	$scope.noMatchFound = false;

	$scope.draw = function(){
		console.log("The user ID is " + User.id);
		Restangular.one("draw", User.id)
		.get()
		.then(function(resp){
			console.log("Returning from draw REST call");
			if (resp.match && resp.match != ""){
				$scope.drawMatch(resp.match);
			} else
			{
				$scope.noMatchFound = true;
			}
		},
		function(data){
			console.log("Error");
			console.log(data);
			$scope.drawError = data.statusText;
			$scope.isDrawError = true;
		});
	};

	$scope.drawMatch = function(email){
		Restangular.one("colleague", email)
		.get()
		.then(function(data){
			console.log("Setting match record: " + data.colleague);
			User.match = data.colleague;
			$location.path("/match");

		})
	}
}]);

santa.controller("Match", ["$scope", "User", '$sce', "$location", function($scope, User, $sce, $location) {

	if (!User.match || !User.match.litho){
		$location.path("/");
	}

	$scope.match = User.match;

}]);