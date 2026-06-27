import 'package:equatable/equatable.dart';

abstract class AuthEvent extends Equatable {
  const AuthEvent();

  @override
  List<Object?> get props => [];
}

class AuthCheckEvent extends AuthEvent {}

class LoginEvent extends AuthEvent {
  final String email;
  final String password;

  const LoginEvent({required this.email, required this.password});

  @override
  List<Object?> get props => [email, password];
}

class RegisterEvent extends AuthEvent {
  final String name;
  final String email;
  final String password;
  final String language;

  const RegisterEvent({
    required this.name,
    required this.email,
    required this.password,
    required this.language,
  });

  @override
  List<Object?> get props => [name, email, password, language];
}

class UpdateProfileEvent extends AuthEvent {
  final String name;
  final String language;

  const UpdateProfileEvent({required this.name, required this.language});

  @override
  List<Object?> get props => [name, language];
}

class LogoutEvent extends AuthEvent {}
