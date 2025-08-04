public class CommonDialogContent extends BaseDialogFragment {

    private RelativeLayout cdcTitleRl;
    private TextView       cdcTitleTv;
    private RelativeLayout cdcContentRl;
    private TextView       cdcContentTv;
    private RelativeLayout cdcBottomRl;
    private TextView       cdcBottomPositiveTv;
    private TextView       cdcBottomNegativeTv;

    private DialogConfig config;

    public record BtnAction(CharSequence text, View.OnClickListener listener) { }

    public record DialogConfig(CharSequence title,
                               CharSequence content,
                               BtnAction positive,
                               BtnAction negative) { }

    public CommonDialogContent init(Context context, DialogConfig cfg) {
        this.config = cfg;
        super.init(context, new DialogLayoutCallback() {

            @Override public int bindTheme()   { return R.style.CommonContentDialogStyle; }
            @Override public int bindLayout()  { return R.layout.common_dialog_content; }

            @Override
            public void initView(BaseDialogFragment dialog, View v) {          
                cdcTitleRl            = v.findViewById(R.id.cdcTitleRl);
                cdcTitleTv            = v.findViewById(R.id.cdcTitleTv);
                cdcContentRl          = v.findViewById(R.id.cdcContentRl);
                cdcContentTv          = v.findViewById(R.id.cdcContentTv);
                cdcBottomRl           = v.findViewById(R.id.cdcBottomRl);
                cdcBottomPositiveTv   = v.findViewById(R.id.cdcBottomPositiveTv);
                cdcBottomNegativeTv   = v.findViewById(R.id.cdcBottomNegativeTv);

                if (TextUtils.isEmpty(config.title())) {
                    cdcTitleRl.setVisibility(View.GONE);
                } else {
                    cdcTitleTv.setText(config.title());
                }

                if (TextUtils.isEmpty(config.content())) {
                    cdcContentRl.setVisibility(View.GONE);
                } else {
                    cdcContentTv.setText(config.content());
                }

                BtnAction pos = config.positive();
                BtnAction neg = config.negative();
                if (pos == null && neg == null) {
                    cdcBottomRl.setVisibility(View.GONE);
                } else {
                    if (pos != null) {
                        ClickUtils.applyPressedBgDark(cdcBottomPositiveTv);
                        cdcBottomPositiveTv.setText(pos.text());
                        cdcBottomPositiveTv.setOnClickListener(v1 -> {
                            dismiss();
                            pos.listener().onClick(v1);
                        });
                    }
                    if (neg != null) {
                        ClickUtils.applyPressedBgDark(cdcBottomNegativeTv);
                        cdcBottomNegativeTv.setText(neg.text());
                        cdcBottomNegativeTv.setOnClickListener(v12 -> {
                            dismiss();
                            neg.listener().onClick(v12);
                        });
                    }
                }
            }

            @Override public void setWindowStyle(Window w) { }
            @Override public void onCancel(BaseDialogFragment d) { }
            @Override public void onDismiss(BaseDialogFragment d) { }
        });
        return this;
    }
}
