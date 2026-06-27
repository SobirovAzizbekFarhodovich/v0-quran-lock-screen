import 'package:equatable/equatable.dart';

abstract class SpiritualEvent extends Equatable {
  const SpiritualEvent();

  @override
  List<Object?> get props => [];
}

class SendSpiritualQueryEvent extends SpiritualEvent {
  final String message;
  final String language;

  const SendSpiritualQueryEvent({required this.message, required this.language});

  @override
  List<Object?> get props => [message, language];
}

class LoadSpiritualHistoryEvent extends SpiritualEvent {}
