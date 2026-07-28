import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../app/colors.dart';
import '../app/text_styles.dart';
import '../app/spacing.dart';
import '../core/di/injector.dart';
import '../services/api_client.dart';
import '../services/recitation_service.dart';
import '../features/recitation/presentation/bloc/recitation_bloc.dart';
import '../features/recitation/presentation/bloc/recitation_event.dart';
import '../features/recitation/presentation/bloc/recitation_state.dart';
import '../features/recitation/presentation/widgets/mic_button.dart';
import '../features/recitation/presentation/widgets/waveform_widget.dart';
import '../features/recitation/presentation/widgets/score_widget.dart';
import '../features/recitation/presentation/widgets/tajweed_widget.dart';
import '../models/quran_data.dart';

class RecitationScreen extends StatefulWidget {
  const RecitationScreen({super.key});

  @override
  State<RecitationScreen> createState() => _RecitationScreenState();
}

class _RecitationScreenState extends State<RecitationScreen> {
  late RecitationBloc _bloc;
  
  // Current selections
  int _selectedSurahIndex = 0;
  int _selectedAyahIndex = 0;

  @override
  void initState() {
    super.initState();
    final recitationService = getIt<RecitationService>();
    _bloc = RecitationBloc(recitationService, getIt<ApiClient>());
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = isDark ? AppColors.darkSurface : Colors.white;
    final textColor = isDark ? AppColors.darkText : AppColors.textPrimary;
    final textSecondary = isDark ? AppColors.darkText2 : AppColors.textSecondary;
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;

    final currentSurah = QuranData.surahs[_selectedSurahIndex];
    final currentVerse = currentSurah.verses[_selectedAyahIndex];

    return BlocProvider<RecitationBloc>(
      create: (context) => _bloc,
      child: Scaffold(
        backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
        appBar: AppBar(
          title: const Text('Qiroat Tekshiruvi'),
          backgroundColor: isDark ? AppColors.darkBackground : AppColors.primary,
          foregroundColor: Colors.white,
          elevation: 0,
        ),
        body: BlocBuilder<RecitationBloc, RecitationState>(
          builder: (context, state) {
            final isRecording = state is RecitationRecording;
            final isProcessing = state is RecitationProcessing;

            return SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.xl),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // SURAH HORIZONTAL SELECTOR CHIPS (Only shown when not recording)
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
                            padding: const EdgeInsets.only(right: 8.0),
                            child: FilterChip(
                              label: Text(
                                '${surah.name} (${surah.arabicName})',
                                style: AppTextStyles.labelMedium.copyWith(
                                  color: isSelected
                                      ? (isDark ? AppColors.primaryDark : Colors.white)
                                      : (isDark ? AppColors.darkText : AppColors.textPrimary),
                                  fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                ),
                              ),
                              selected: isSelected,
                              selectedColor: isDark ? AppColors.primaryLight : AppColors.primary,
                              checkmarkColor: isSelected ? (isDark ? AppColors.primaryDark : Colors.white) : null,
                              backgroundColor: isDark ? AppColors.darkSurface : Colors.white,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(20),
                                side: BorderSide(color: isSelected ? Colors.transparent : borderColor),
                              ),
                              onSelected: (_) {
                                setState(() {
                                  _selectedSurahIndex = index;
                                  _selectedAyahIndex = 0; // Reset to first ayah of surah
                                });
                              },
                            ),
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                  ],

                  // MAIN ARABIC AYAH CONTAINER
                  Container(
                    decoration: BoxDecoration(
                      color: cardBg,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: borderColor),
                      boxShadow: [
                        BoxShadow(
                          color: isDark ? Colors.transparent : Colors.black.withValues(alpha: 0.04),
                          blurRadius: 10,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    child: Column(
                      children: [
                        // Surah & Ayah metadata heading
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '${currentSurah.name} ${currentSurah.id}:${currentVerse.id}-oyat',
                              style: AppTextStyles.labelMedium.copyWith(color: textSecondary),
                            ),
                            Text(
                              currentSurah.arabicName,
                              style: AppTextStyles.arabicSmall.copyWith(color: AppColors.gold, fontWeight: FontWeight.bold),
                            ),
                          ],
                        ),
                        const SizedBox(height: AppSpacing.x2l),
                        
                        // Centered Arabic text
                        Directionality(
                          textDirection: TextDirection.rtl,
                          child: SelectableText(
                            currentVerse.arabicText,
                            textAlign: TextAlign.center,
                            style: AppTextStyles.arabicLarge.copyWith(
                              color: isDark ? AppColors.darkText2 : AppColors.primary,
                              fontSize: 28,
                            ),
                          ),
                        ),
                        const SizedBox(height: AppSpacing.xl),
                        
                        // Transliteration
                        Text(
                          currentVerse.transliteration,
                          textAlign: TextAlign.center,
                          style: AppTextStyles.bodyMedium.copyWith(
                            fontStyle: FontStyle.italic,
                            color: textSecondary,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        
                        // Translation
                        Text(
                          currentVerse.translation,
                          textAlign: TextAlign.center,
                          style: AppTextStyles.bodyMedium.copyWith(
                            color: textColor,
                          ),
                        ),
                        
                        if (!isRecording) ...[
                          const SizedBox(height: AppSpacing.lg),
                          Divider(color: borderColor),
                          const SizedBox(height: AppSpacing.xs),
                          
                          // Ayah controls (Previous / Next)
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.arrow_back_ios_new_rounded),
                                color: isDark ? AppColors.primaryLight : AppColors.primary,
                                onPressed: _selectedAyahIndex > 0
                                    ? () {
                                        setState(() {
                                          _selectedAyahIndex--;
                                        });
                                      }
                                    : null,
                              ),
                              Text(
                                '${_selectedAyahIndex + 1} / ${currentSurah.verses.length}',
                                style: AppTextStyles.labelMedium.copyWith(
                                  color: textColor,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              IconButton(
                                icon: const Icon(Icons.arrow_forward_ios_rounded),
                                color: isDark ? AppColors.primaryLight : AppColors.primary,
                                onPressed: _selectedAyahIndex < currentSurah.verses.length - 1
                                    ? () {
                                        setState(() {
                                          _selectedAyahIndex++;
                                        });
                                      }
                                    : null,
                              ),
                            ],
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: AppSpacing.x3l),

                  // Waveform recording status banner (when recording)
                  if (isRecording || isProcessing) ...[
                    Center(
                      child: Column(
                        children: [
                          Text(
                            isProcessing ? 'Tahlil qilinmoqda...' : 'Tilovat tinglanmoqda...',
                            style: AppTextStyles.labelLarge.copyWith(
                              color: isProcessing ? AppColors.primary : AppColors.error,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: AppSpacing.sm),
                          Text(
                            isProcessing ? 'Natija tayyorlanmoqda' : 'Oyatni ovoz chiqarib tilovat qiling',
                            style: AppTextStyles.labelSmall.copyWith(color: textSecondary),
                          ),
                          const SizedBox(height: AppSpacing.md),
                        ],
                      ),
                    ),
                  ],

                  // Pulsating mic button
                  MicButton(
                    isRecording: isRecording,
                    onTap: () {
                      if (isProcessing) {
                        return;
                      }
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
                  const SizedBox(height: AppSpacing.xl),

                  // Staggered animated audio waveform bars
                  WaveformWidget(isRecording: isRecording),
                  const SizedBox(height: AppSpacing.xl),

                  // Recitation result (Score and highlights)
                  if (state is RecitationResultState) ...[
                    ScoreWidget(
                      score: state.score,
                      errorCount: state.errors.length,
                      wordCount: state.transcription.split(' ').length,
                    ),
                    const SizedBox(height: AppSpacing.xl),

                    Container(
                      decoration: BoxDecoration(
                        color: cardBg,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: borderColor),
                      ),
                      padding: const EdgeInsets.all(AppSpacing.md),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(
                            'Siz o\'qigan matn:',
                            style: AppTextStyles.labelMedium.copyWith(color: textSecondary),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '"${state.transcription}"',
                            style: AppTextStyles.bodyLarge.copyWith(
                              color: textColor,
                              fontStyle: FontStyle.italic,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: AppSpacing.xl),

                    // Highlight details of tajweed rules errors
                    TajweedWidget(errors: state.errors),
                  ],

                  // Recitation error banner
                  if (state is RecitationErrorState) ...[
                    Container(
                      decoration: BoxDecoration(
                        color: AppColors.error.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppColors.error.withValues(alpha: 0.3)),
                      ),
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        children: [
                          const Icon(Icons.error_outline_rounded, color: AppColors.error),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              state.error,
                              style: AppTextStyles.bodyMedium.copyWith(color: AppColors.error),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            );
          },
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
