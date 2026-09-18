package com.netessx.qutschedule.ui;

import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.Nullable;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Profile;

/** 我的信息：昵称、签名与学校信息，全部只存在本机。 */
public class ProfileActivity extends BaseActivity {

    private EditText nicknameInput;
    private EditText signatureInput;
    private EditText schoolInput;
    private EditText collegeInput;
    private EditText majorInput;
    private EditText gradeInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageTitle(getString(R.string.mine_profile));
        setPageContent(getLayoutInflater().inflate(R.layout.activity_profile, null, false));

        nicknameInput = findViewById(R.id.et_nickname);
        signatureInput = findViewById(R.id.et_signature);
        schoolInput = findViewById(R.id.et_school);
        collegeInput = findViewById(R.id.et_college);
        majorInput = findViewById(R.id.et_major);
        gradeInput = findViewById(R.id.et_grade);

        Profile profile = ScheduleStore.get(this).data().profile;
        nicknameInput.setText(profile.nickname);
        signatureInput.setText(profile.signature);
        schoolInput.setText(profile.school);
        collegeInput.setText(profile.college);
        majorInput.setText(profile.major);
        gradeInput.setText(profile.grade);

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private void save() {
        Profile profile = ScheduleStore.get(this).data().profile;
        profile.nickname = nicknameInput.getText().toString().trim();
        profile.signature = signatureInput.getText().toString().trim();
        profile.school = schoolInput.getText().toString().trim();
        profile.college = collegeInput.getText().toString().trim();
        profile.major = majorInput.getText().toString().trim();
        profile.grade = gradeInput.getText().toString().trim();
        profile.normalize();

        ScheduleStore.get(this).save();
        setResult(RESULT_OK);
        finish();
    }
}
