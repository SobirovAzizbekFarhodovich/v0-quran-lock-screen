import 'package:equatable/equatable.dart';

abstract class RecitationState extends Equatable {
  const RecitationState();

  @override
  List<Object?> get props => [];
}

class RecitationInitial extends RecitationState {}

class RecitationRecording extends RecitationState {
  final int surahNumber;
  final int ayahNumber;

  const RecitationRecording({required this.surahNumber, required this.ayahNumber});

  @override
  List<Object?> get props => [surahNumber, ayahNumber];
}

class RecitationResultState extends RecitationState {
  final String transcription;
  final double score;
  final List<dynamic> errors;
  final String? audioUrl;

  const RecitationResultState({
    required this.transcription,
    required this.score,
    required this.errors,
    this.audioUrl,
  });

  @override
  List<Object?> get props => [transcription, score, errors, audioUrl];
}

class RecitationErrorState extends RecitationState {
  final String error;

  const RecitationErrorState({required this.error});

  @override
  List<Object?> get props => [error];
}
