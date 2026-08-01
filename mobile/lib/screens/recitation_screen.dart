import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../app/colors.dart';
import '../app/spacing.dart';
import '../app/text_styles.dart';
import '../core/di/injector.dart';
import '../features/recitation/presentation/bloc/recitation_bloc.dart';
import '../features/recitation/presentation/bloc/recitation_event.dart';
import '../features/recitation/presentation/bloc/recitation_state.dart';
import '../features/recitation/presentation/widgets/mic_button.dart';
import '../features/recitation/presentation/widgets/score_widget.dart';
import '../features/recitation/presentation/widgets/tajweed_widget.dart';
import '../features/recitation/presentation/widgets/waveform_widget.dart';
import '../models/quran_data.dart';
import '../services/api_client.dart';
import '../services/recitation_service.dart';
import '../widgets/cosmic_background.dart';

class RecitationScreen extends StatefulWidget {
  const RecitationScreen({super.key});

  @override
  State<RecitationScreen> createState() => _RecitationScreenState();
}

class _RecitationScreenState extends State<RecitationScreen> {
  late RecitationBloc _bloc;
  int _selectedSurahIndex = 0;
  int _selectedAyahIndex = 0;

  @override
  void initState() {
    super.initState();
    _bloc = RecitationBloc(getIt<RecitationService>(), getIt<ApiClient>());
  }

  @override
  Widget build(BuildContext context) {
    final currentSurah = QuranData.surahs[_selectedSurahIndex];
    final currentVerse = currentSurah.verses[_selectedAyahIndex];

    return Scaffold(
      body: CosmicBackground(
        showMosque: true,
        child: BlocProvider<RecitationBloc>.value(
          value: _bloc,
          child: BlocBuilder<RecitationBloc, RecitationState>(
            builder: (context, state) {
              final isRecording = state is RecitationRecording;
              final isProcessing = state is RecitationProcessing;

              return CustomScrollView(
                slivers: [
                  SliverAppBar(
                    pinned: true,
                    backgroundColor: Colors.transparent,
                    elevation: 0,
                    title: Text(
                      'Qiroat studiyasi',
                      style: AppTextStyles.headlineMedium.copyWith(color: AppColors.darkText),
                    ),
                    centerTitle: true,
                  ),
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(AppSpacing.xl, 0, AppSpacing.xl, 120),
                    sliver: SliverList(
                      delegate: SliverChildListDelegate([
                        if (!isRecording) ...[
                          SizedBox(
                            height: 42,
                            child: ListView.builder(
                              scrollDirection: Axis.horizontal,
                              itemCount: QuranData.surahs.length,
                              itemBuilder: (context, index) {
                                final surah = QuranData.surahs[index];
                                final isSelected = index == _selectedSurahIndex;
                                return Padding(
                                  padding: const EdgeInsets.only(right: 8),
                                  child: FilterChip(
                                    label: Text(
                                      surah.name,
                                      style: AppTextStyles.labelMedium.copyWith(
                                        color: isSelected ? AppColors.midnightDeep : AppColors.darkText,
                                      ),
                                    ),
                                    selected: isSelected,
                                    selectedColor: AppColors.neonTeal,
                                    backgroundColor: Colors.white.withOpacity(0.08),
                                    side: BorderSide(color: Colors.white.withOpacity(0.12)),
                                    onSelected: (_) => setState(() {
                                      _selectedSurahIndex = index;
                                      _selectedAyahIndex = 0;
                                    }),
                                  ),
                                );
                              },
                            ),
                          ),
                          const SizedBox(height: AppSpacing.lg),
                        ],
                        GlassContainer(
                          padding: const EdgeInsets.all(AppSpacing.lg),
                          child: Column(
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    '${currentSurah.name} ${currentSurah.id}:${currentVerse.id}',
                                    style: AppTextStyles.labelMedium.copyWith(color: AppColors.darkText2),
                                  ),
                                  Text(
                                    currentSurah.arabicName,
                                    style: AppTextStyles.arabicSmall.copyWith(color: AppColors.gold),
                                  ),
                                ],
                              ),
                              const SizedBox(height: AppSpacing.x2l),
                              Directionality(
                                textDirection: TextDirection.rtl,
                                child: SelectableText(
                                  currentVerse.arabicText,
                                  textAlign: TextAlign.center,
                                  style: AppTextStyles.arabicLarge.copyWith(
                                    color: AppColors.darkText,
                                    fontSize: 28,
                                  ),
                                ),
                              ),
                              const SizedBox(height: AppSpacing.lg),
                              Text(
                                currentVerse.transliteration,
                                textAlign: TextAlign.center,
                                style: AppTextStyles.bodyMedium.copyWith(
                                  fontStyle: FontStyle.italic,
                                  color: AppColors.darkText2,
                                ),
                              ),
                              const SizedBox(height: AppSpacing.sm),
                              Text(
                                currentVerse.translation,
                                textAlign: TextAlign.center,
                                style: AppTextStyles.bodyMedium.copyWith(color: AppColors.darkText),
                              ),
                              if (!isRecording) ...[
                                const SizedBox(height: AppSpacing.lg),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    IconButton(
                                      icon: const Icon(Icons.arrow_back_ios_new_rounded),
                                      color: AppColors.neonCyan,
                                      onPressed: _selectedAyahIndex > 0
                                          ? () => setState(() => _selectedAyahIndex--)
                                          : null,
                                    ),
                                    Text(
                                      '${_selectedAyahIndex + 1} / ${currentSurah.verses.length}',
                                      style: AppTextStyles.labelMedium.copyWith(color: AppColors.darkText),
                                    ),
                                    IconButton(
                                      icon: const Icon(Icons.arrow_forward_ios_rounded),
                                      color: AppColors.neonCyan,
                                      onPressed: _selectedAyahIndex < currentSurah.verses.length - 1
                                          ? () => setState(() => _selectedAyahIndex++)
                                          : null,
                                    ),
                                  ],
                                ),
                              ],
                            ],
                          ),
                        ),
                        const SizedBox(height: AppSpacing.x2l),
                        if (isRecording || isProcessing)
                          Center(
                            child: Text(
                              isProcessing ? 'Tahlil qilinmoqda...' : 'Tilovat tinglanmoqda...',
                              style: AppTextStyles.labelLarge.copyWith(
                                color: isProcessing ? AppColors.neonTeal : AppColors.error,
                              ),
                            ),
                          ),
                        const SizedBox(height: AppSpacing.md),
                        MicButton(
                          isRecording: isRecording,
                          onTap: () {
                            if (isProcessing) return;
                            if (isRecording) {
                              _bloc.add(StopRecitationEvent());
                            } else {
                              _bloc.add(StartRecitationEvent(
                                surahNumber: currentSurah.id,
                                ayahNumber: currentVerse.id,
                              ));
                            }
                          },
                        ),
                        const SizedBox(height: AppSpacing.lg),
                        WaveformWidget(isRecording: isRecording),
                        if (state is RecitationResultState) ...[
                          const SizedBox(height: AppSpacing.xl),
                          ScoreWidget(
                            score: state.score,
                            errorCount: state.errors.length,
                            wordCount: state.transcription.split(' ').length,
                          ),
                          const SizedBox(height: AppSpacing.lg),
                          GlassContainer(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Text('Siz o\'qigan matn:', style: AppTextStyles.labelMedium.copyWith(color: AppColors.darkText2)),
                                const SizedBox(height: 6),
                                Text(
                                  state.transcription,
                                  style: AppTextStyles.bodyLarge.copyWith(color: AppColors.darkText, fontStyle: FontStyle.italic),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: AppSpacing.lg),
                          TajweedWidget(errors: state.errors),
                        ],
                        if (state is RecitationErrorState)
                          GlassContainer(
                            borderColor: AppColors.error.withOpacity(0.4),
                            child: Row(
                              children: [
                                const Icon(Icons.error_outline, color: AppColors.error),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Text(state.error, style: AppTextStyles.bodyMedium.copyWith(color: AppColors.error)),
                                ),
                              ],
                            ),
                          ),
                      ]),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _bloc.close();
    super.dispose();
  }
}
