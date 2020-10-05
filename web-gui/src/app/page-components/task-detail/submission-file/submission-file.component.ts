import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {CourseTask} from "../../../model/HttpInterfaces";

@Component({
  selector: 'app-submission-file',
  templateUrl: './submission-file.component.html',
  styleUrls: ['./submission-file.component.scss']
})
export class SubmissionFileComponent implements OnInit {
  @Input() task: CourseTask;
  @Input() deadlineTask:any;

  @Output() update: EventEmitter<any> = new EventEmitter<any>();
  @Output() trigger: EventEmitter<CourseTask> = new EventEmitter<CourseTask>();

  submissionFile: File[] = [];
  constructor() { }

  ngOnInit() {

  }
  triggerInfo(){
    this.trigger.emit(this.task)
  }

  updateSubmissionFile(event) {
    this.submissionFile = event.addedFiles
    this.update.emit({taskid: this.task.task_id, content: this.submissionFile[0]})
  }
}