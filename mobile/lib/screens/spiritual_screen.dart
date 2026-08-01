import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';

import '../app/colors.dart';
import '../app/text_styles.dart';
import '../app/spacing.dart';
import '../core/di/injector.dart';
import '../services/api_client.dart';
import '../features/spiritual/presentation/bloc/spiritual_bloc.dart';
import '../features/spiritual/presentation/bloc/spiritual_event.dart';
import '../features/spiritual/presentation/bloc/spiritual_state.dart';
import '../features/spiritual/presentation/widgets/chat_bubble.dart';
import '../features/spiritual/presentation/widgets/source_card.dart';
import '../features/spiritual/presentation/widgets/typing_indicator.dart';

class ChatMessage {
  final String text;
  final bool isUser;
  final List<dynamic>? sources;
  final String time;

  ChatMessage({
    required this.text,
    required this.isUser,
    required this.time,
    this.sources,
  });
}

class SpiritualScreen extends StatefulWidget {
  const SpiritualScreen({super.key});

  @override
  State<SpiritualScreen> createState() => _SpiritualScreenState();
}

class _SpiritualScreenState extends State<SpiritualScreen> {
  late SpiritualBloc _bloc;
  final _controller = TextEditingController();
  final List<ChatMessage> _messages = [];
  final _scrollController = ScrollController();

  final List<String> _quickStates = [
    'Charchadim 😔',
    'Ko\'nglim g\'ash 😕',
    'Qo\'rqyapman 😨',
    'Siqilyapman 😭',
    'Xursandman 😊',
    'Tashvishdaman 😰',
  ];

  @override
  void initState() {
    super.initState();
    _bloc = SpiritualBloc(getIt<ApiClient>());
    
    // Add initial greeting message
    _messages.add(ChatMessage(
      text: "Assalamu alaykum. Bugun o'zingizni qanday his qilyapsiz? Ruhiy holatingiz haqida yozing, Qur'on va Hadislardan taskin izlaymiz.",
      isUser: false,
      time: DateFormat('HH:mm').format(DateTime.now()),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardBg = isDark ? AppColors.darkSurface : Colors.white;
    final borderColor = isDark ? AppColors.darkBorder : AppColors.border;
    final textSecondary = isDark ? AppColors.darkText2 : AppColors.textSecondary;
    final bottomPadding = MediaQuery.of(context).padding.bottom;

    return BlocProvider<SpiritualBloc>(
      create: (context) => _bloc,
      child: Scaffold(
        backgroundColor: isDark ? AppColors.darkBackground : AppColors.background,
        appBar: AppBar(
          title: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Text('Ruhiy Taskin'),
              Text(
                'Qur\'on va Hadis asosida',
                style: AppTextStyles.labelSmall.copyWith(color: Colors.white.withOpacity(0.8), fontSize: 10),
              ),
            ],
          ),
          backgroundColor: isDark ? AppColors.darkBackground : AppColors.primary,
          foregroundColor: Colors.white,
          elevation: 0,
        ),
        body: BlocConsumer<SpiritualBloc, SpiritualState>(
          listener: (context, state) {
            if (state is SpiritualMessageReceived) {
              setState(() {
                _messages.add(ChatMessage(
                  text: state.answer.answer,
                  isUser: false,
                  sources: state.answer.sources,
                  time: DateFormat('HH:mm').format(DateTime.now()),
                ));
              });
              _scrollToBottom();
            } else if (state is SpiritualError) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(state.message), 
                  backgroundColor: AppColors.error,
                  behavior: SnackBarBehavior.floating,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              );
            }
          },
          builder: (context, state) {
            final isLoading = state is SpiritualLoading;

            return Column(
              children: [
                // QUICK STATE SELECTOR CHIPS
                if (_messages.length <= 1) ...[
                  const SizedBox(height: AppSpacing.sm),
                  SizedBox(
                    height: 40,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
                      itemCount: _quickStates.length,
                      itemBuilder: (context, index) {
                        final chipText = _quickStates[index];
                        return Padding(
                          padding: const EdgeInsets.only(right: 8.0),
                          child: ActionChip(
                            label: Text(
                              chipText,
                              style: AppTextStyles.labelMedium.copyWith(
                                color: isDark ? AppColors.darkText : AppColors.textPrimary,
                              ),
                            ),
                            backgroundColor: cardBg,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(20),
                              side: BorderSide(color: borderColor),
                            ),
                            onPressed: () {
                              _controller.text = "O'zimni $chipText his qilyapman.";
                              _sendMessage();
                            },
                          ),
                        );
                      },
                    ),
                  ),
                ],

                // CHAT MESSAGES AREA
                Expanded(
                  child: ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.all(AppSpacing.xl),
                    itemCount: _messages.length + (isLoading ? 1 : 0),
                    itemBuilder: (context, index) {
                      if (index == _messages.length) {
                        return const Padding(
                          padding: EdgeInsets.symmetric(vertical: 8.0),
                          child: TypingIndicator(),
                        );
                      }

                      final msg = _messages[index];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            ChatBubble(
                              text: msg.text,
                              isUser: msg.isUser,
                              time: msg.time,
                            ),
                            // Render Sources list if available
                            if (!msg.isUser && msg.sources != null && msg.sources!.isNotEmpty) ...[
                              const SizedBox(height: 6),
                              ...msg.sources!.map((source) {
                                return SourceCard(
                                  book: source['book']?.toString() ?? 'Manba',
                                  chapter: source['chapter']?.toString() ?? '',
                                );
                              }),
                            ],
                          ],
                        ),
                      );
                    },
                  ),
                ),

                // INPUT CONTROLLER FLOAT CARD
                Container(
                  padding: EdgeInsets.fromLTRB(16, 12, 16, bottomPadding > 0 ? bottomPadding : 16),
                  decoration: BoxDecoration(
                    color: cardBg,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.04),
                        blurRadius: 10,
                        offset: const Offset(0, -4),
                      )
                    ],
                    border: Border(top: BorderSide(color: borderColor, width: 0.5)),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _controller,
                          minLines: 1,
                          maxLines: 4,
                          style: AppTextStyles.bodyLarge.copyWith(
                            color: isDark ? AppColors.darkText : AppColors.textPrimary,
                          ),
                          decoration: InputDecoration(
                            hintText: 'Holatingizni yozing...',
                            hintStyle: AppTextStyles.bodyMedium.copyWith(color: textSecondary),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                            filled: true,
                            fillColor: isDark ? AppColors.darkSurface2 : AppColors.background,
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: BorderSide(color: borderColor),
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: BorderSide(color: borderColor),
                            ),
                            focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: BorderSide(
                                color: isDark ? AppColors.primaryLight : AppColors.primary,
                                width: 1.5,
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      CircleAvatar(
                        backgroundColor: isDark ? AppColors.primaryLight : AppColors.primary,
                        radius: 24,
                        child: IconButton(
                          icon: Icon(
                            Icons.send_rounded,
                            color: isDark ? AppColors.primaryDark : Colors.white,
                            size: 20,
                          ),
                          onPressed: isLoading ? null : _sendMessage,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  void _sendMessage() {
    final text = _controller.text.trim();
    if (text.isNotEmpty) {
      setState(() {
        _messages.add(ChatMessage(
          text: text,
          isUser: true,
          time: DateFormat('HH:mm').format(DateTime.now()),
        ));
      });
      _controller.clear();
      _scrollToBottom();
      
      _bloc.add(SendSpiritualQueryEvent(message: text, language: 'uz'));
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    _bloc.close();
    super.dispose();
  }
}
