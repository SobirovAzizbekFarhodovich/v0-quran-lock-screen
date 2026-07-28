import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../../../../app/colors.dart';

class WaveformWidget extends StatefulWidget {
  final bool isRecording;

  const WaveformWidget({
    super.key,
    required this.isRecording,
  });

  @override
  State<WaveformWidget> createState() => _WaveformWidgetState();
}

class _WaveformWidgetState extends State<WaveformWidget> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  
  // Heights mapping from design specs: bar1..bar7
  final List<double> _maxHeights = [20.0, 30.0, 26.0, 22.0, 18.0, 28.0, 16.0];
  final List<double> _delays = [0.0, 0.15, 0.30, 0.45, 0.60, 0.75, 0.90];

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    );
    if (widget.isRecording) {
      _controller.repeat();
    }
  }

  @override
  void didUpdateWidget(covariant WaveformWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isRecording && !oldWidget.isRecording) {
      _controller.repeat();
    } else if (!widget.isRecording && oldWidget.isRecording) {
      _controller.stop();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.isRecording) {
      return const SizedBox(height: 40);
    }

    return SizedBox(
      height: 40,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          return Row(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: List.generate(_maxHeights.length, (index) {
              final double maxH = _maxHeights[index];
              final double delay = _delays[index];
              
              // Calculate phase wave based on progress and stagger delay
              final double phase = (_controller.value * 2 * math.pi) - (delay * math.pi);
              final double factor = (math.sin(phase).abs());
              final double h = 6.0 + (factor * (maxH - 6.0));

              return Container(
                width: 4,
                height: h,
                margin: const EdgeInsets.symmetric(horizontal: 2.0),
                decoration: BoxDecoration(
                  color: AppColors.primaryLight,
                  borderRadius: BorderRadius.circular(2),
                ),
              );
            }),
          );
        },
      ),
    );
  }
}
